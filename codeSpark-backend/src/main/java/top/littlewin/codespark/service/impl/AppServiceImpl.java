package top.littlewin.codespark.service.impl;

import jakarta.annotation.Resource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.AiCodeGenTypeRoutingService;
import top.littlewin.codespark.ai.AiAppNamingService;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.core.AICodeGeneratorFacade;
import top.littlewin.codespark.core.builder.BuildResult;
import top.littlewin.codespark.core.builder.VueProjectBulider;
import top.littlewin.codespark.core.stream.StreamMessageHandler;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.dto.app.AppAddRequest;
import top.littlewin.codespark.model.dto.app.AppQueryRequest;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.mapper.AppMapper;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.ChatHistoryMessageTypeEnum;
import top.littlewin.codespark.model.enums.ChatStageEnum;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;
import top.littlewin.codespark.model.vo.AppVO;
import top.littlewin.codespark.model.vo.UserVO;
import top.littlewin.codespark.monitor.MonitorContext;
import top.littlewin.codespark.monitor.MonitorContextHolder;
import top.littlewin.codespark.service.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用表 服务层实现。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Slf4j
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    @Resource
    private UserService userService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AICodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private StreamMessageHandler streamMessageHandler;

    @Resource
    private VueProjectBulider vueProjectBulider;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private FileService fileService;

    @Resource
    private UserQuotaUsageService userQuotaUsageService;

    /** 自注入代理：createApp 内部调用 @Async 方法时，需通过代理走异步线程池 */
    @Lazy
    @Resource
    private AppService self;

    /** AI 应用命名开关：false 时创建应用直接使用提示词截取名，不调用 AI（默认开启，环境变量 APP_NAME_ENABLED 可覆盖） */
    @Value("${codespark.ai.app-name.enabled:true}")
    private boolean appNameAiEnabled;

    /**
     * AI 生成失败时的兜底提示（写入聊天历史，避免空消息）
     */
    private static final String GENERATE_FAILED_MESSAGE = "应用生成失败，请重试~";

    /** AI 类型路由：轻量模型（deepseek-chat）判断生成类型 */
    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    /** AI 应用命名：轻量模型（deepseek-chat），独立服务 */
    @Resource
    private AiAppNamingService aiAppNamingService;

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {

        // 1. 参数校验
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_INIT_PROMPT);

        // 2. 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());

        // 3. 先使用截取名称快速落库，避免等待 AI 生成名称阻塞创建请求
        app.setAppName(this.truncateName(initPrompt));

        // 4. 由 AI 判断任务难度来选择不同的生成类型（轻量模型）；
        // 路由偶发解析失败时降级为 MULTI_FILE，避免创建流程被 AI 不稳定拖垮
        CodeGenTypeEnum codeGenType;
        try {
            MonitorContextHolder.setContext(MonitorContext.builder()
                    .userId(loginUser.getId().toString())
                    .appId("routing")
                    .userAccount(loginUser.getUserAccount())
                    .build()
            );
            codeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        } catch (Exception e) {
            log.warn("AI 类型路由失败，降级为 MULTI_FILE: {}", e.getMessage());
            codeGenType = CodeGenTypeEnum.MULTI_FILE;
        } finally {
            MonitorContextHolder.clearContext();
        }

        // 5. 路由结果必须是有效的生成类型（html / multi_file / vue）
        ThrowUtils.throwIf(codeGenType == null,
                ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_CODE_GEN_TYPE);
        app.setCodeGenType(codeGenType.getValue());

        // 6. 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 7. 开启 AI 命名开关时，异步生成应用名称并更新（不阻塞响应，名称生成后前端刷新可见）；
        // 关闭时保持提示词截取名（后续用户可在编辑中手动改名）
        if (appNameAiEnabled) {
            self.updateAppNameAsync(app.getId(), initPrompt, loginUser.getId(), loginUser.getUserAccount());
        } else {
            log.info("AI 应用命名已关闭（codespark.ai.app-name.enabled=false），应用名使用提示词截取: appId={}", app.getId());
        }

        return app.getId();
    }

    /**
     * 异步生成并更新应用名称（不阻塞创建请求）
     */
    @Async
    @Override
    public void updateAppNameAsync(Long appId, String userMessage, Long userId, String userAccount) {
        // 开关防御：关闭时直接跳过，避免覆盖用户已手动修改的应用名
        if (!appNameAiEnabled) {
            log.info("AI 应用命名已关闭，跳过异步更新名称: appId={}", appId);
            return;
        }

        try {
            MonitorContextHolder.setContext(MonitorContext.builder()
                    .userId(userId.toString())
                    .appId("naming")
                    .userAccount(userAccount)
                    .build());
            String name = this.generateAppName(userMessage);
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setAppName(name);
            this.updateById(updateApp);
            log.info("异步更新应用名称成功: appId={}, appName={}", appId, name);
        } catch (Exception e) {
            log.error("异步更新应用名称失败: appId={}", appId, e);
        } finally {
            MonitorContextHolder.clearContext();
        }
    }

    @Override
    public Flux<StreamMessage> chatToGenCode(Long appId, String message, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_APP_ID);
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_CHAT_MESSAGE);

        // 2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, ErrorMessage.APP_NOT_FOUND);

        // 3.权限校验，仅本人可以和 AI 对话
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        // 4 校验月额度
        userQuotaUsageService.checkQuota(loginUser.getId());

        // 5. 应用代码类型
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null, ErrorCode.SYSTEM_ERROR, ErrorMessage.INVALID_CODE_GEN_TYPE);

        // 6. 判断对话阶段：已有 AI 历史 → 修改应用；否则 → 创建应用（VUE 模式据此切换系统提示词）
        ChatStageEnum chatStage = chatHistoryService.hasAiChatMessage(appId)
                ? ChatStageEnum.MODIFY
                : ChatStageEnum.CREATE;

        // 7. 保存用户消息
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());

        // 8. 设置监控上下文（用户ID、应用ID 和用户账号；userAccount 注册即必填，用于 Grafana 排行展示）
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .userAccount(loginUser.getUserAccount())
                        .build()
        );

        // 9. 调用 AI 生成代码
        Flux<StreamMessage> contentStream =  aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenType, appId, chatStage);

        // 10. 渲染展示文本 + 保存 AI 响应结果
        return streamMessageHandler.handle(contentStream, appId, loginUser)
                .doFinally(signalType -> {
                    // 10. 流结束时清理监控上下文
                    MonitorContextHolder.clearContext();
                });
    }

    @Override
    public String deployApp(Long appId, User loginUser) {

        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_APP_ID);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, ErrorMessage.APP_NOT_FOUND);

        // 3. 权限校验，仅本人可以部署自己生成的应用
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        // 4. 检查是否已有 deployKey；若没有，则生成 6 位 deployKey（字母 + 数字）
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)){
            deployKey = RandomUtil.randomString(6);
            app.setDeployKey(deployKey);
        }

        // 5. 获取代码生成类型，获取原始代码生成路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

        // 6. 检查路径是否存在
        File sourceDir  = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(), ErrorCode.SYSTEM_ERROR, ErrorMessage.APP_CODE_NOT_GENERATED);

        // 7. 复制文件到部署目录，为保证vue能够顺利部署，需要在构建一遍
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT){

            // Vue 项目构建
            BuildResult buildResult = vueProjectBulider.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildResult.isSuccess(), ErrorCode.SYSTEM_ERROR,
                    ErrorMessage.VUE_BUILD_FAILED + ": " + buildResult.getMessage());

            // 检查 dist目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, ErrorMessage.VUE_DIST_NOT_FOUND);

            // 构建成功，将源目录设置为 dist 目录
            sourceDir = distDir;
        }

        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        }catch (Exception e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, ErrorMessage.APP_DEPLOY_FAILED);
        }

        // 9. 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        if (!updateResult) {
            // 清理已复制的部署目录，避免"文件已部署、数据库未记录"的状态不一致
            FileUtil.del(deployDirPath);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, ErrorMessage.APP_DEPLOY_UPDATE_FAILED);
        }

        // 10. 部署成功后异步生成封面（稳定，减少多次生成浪费OSS）
        String deployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        this.generateAppScreenshotAsync(appId, deployUrl);

        // 11. 返回可访问的 URL
        return deployUrl;
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行，不阻塞 SSE 响应流；任何失败只记日志，不影响主流程
        Thread.startVirtualThread(() -> {
            try {
                // 调用截图服务生成截图并保存（优先 OSS，失败本地回退），返回稳定标识或本地 URL
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl, appId);
                if (StrUtil.isBlank(screenshotUrl)) {
                    log.error("应用封面截图上传结果为空，跳过更新: appId={}, appUrl={}", appId, appUrl);
                    return;
                }
                // 更新应用封面字段
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, ErrorMessage.UPDATE_COVER_FAILED);
                log.info("应用封面截图更新成功: appId={}, url={}", appId, screenshotUrl);
            } catch (Exception e) {
                log.error("应用封面截图失败: appId={}, appUrl={}", appId, appUrl, e);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAppAndFiles(Long appId) {
        // 1. 查询应用信息（需要 codeGenType 和 deployKey 来定位磁盘目录）
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, ErrorMessage.APP_NOT_FOUND);

        // 2. 删除生成目录：code_output/{codeGenType}_{appId}
        String codeGenType = app.getCodeGenType();
        String sourceDir = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + codeGenType + "_" + appId;
        try {
            FileUtil.del(sourceDir);
        } catch (Exception e) {
            // 目录删除失败不阻断数据库删除，记录日志便于后续人工清理
            log.error("删除应用生成目录失败: {}", sourceDir, e);
        }

        // 3. 若应用已部署，删除部署目录：code_deploy/{deployKey}
        String deployKey = app.getDeployKey();
        if (StrUtil.isNotBlank(deployKey)) {
            String deployDir = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
            try {
                FileUtil.del(deployDir);
            } catch (Exception e) {
                log.error("删除应用部署目录失败: {}", deployDir, e);
            }
        }

        // 4. 删除封面：按存储标识路由删除（OSS 对象 / 本地文件），并清理本地封面目录兜底
        String cover = app.getCover();
        if (StrUtil.isNotBlank(cover)) {
            try {
                fileService.delete(cover);
            } catch (Exception e) {
                log.error("删除应用封面失败: appId={}, cover={}", appId, cover, e);
            }
        }
        String coverDir = AppConstant.APP_COVER_ROOT_DIR + File.separator + appId;
        try {
            FileUtil.del(coverDir);
        } catch (Exception e) {
            log.error("删除应用封面目录失败: {}", coverDir, e);
        }

        // 5. 删除此应用的对话历史（失败不阻断应用删除，记录日志便于后续清理）
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用对话历史失败: appId={}", appId, e);
        }

        // 6. 删除数据库记录（关键步骤，失败需抛出让调用方感知）
        boolean removed = this.removeById(appId);
        if (!removed) {
            log.error("删除应用记录失败: appId={}", appId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, ErrorMessage.APP_DELETE_FAILED);
        }
        return true;
    }


    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 封面动态解析：cover 下发可访问 URL，coverKey 仅在原始值为两态存储标识时下发（供前端编辑回显提交）
        appVO.setCoverKey(storageKey(app.getCover()));
        appVO.setCover(fileService.resolveUrl(appVO.getCover()));
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtil.copyProperties(app, appVO);
            // 封面动态解析：cover 下发可访问 URL，coverKey 仅在原始值为两态存储标识时下发（供前端编辑回显提交）
            appVO.setCoverKey(storageKey(app.getCover()));
            appVO.setCover(fileService.resolveUrl(appVO.getCover()));
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }


    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        // 必须用实体属性引用，PG 驼峰列名才会正确加引号
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(App::getId).eq(id)
                .and(App::getAppName).like(appName, StrUtil.isNotBlank(appName))
                .and(App::getCover).like(cover, StrUtil.isNotBlank(cover))
                .and(App::getInitPrompt).like(initPrompt, StrUtil.isNotBlank(initPrompt))
                .and(App::getCodeGenType).eq(codeGenType, StrUtil.isNotBlank(codeGenType))
                .and(App::getDeployKey).eq(deployKey, StrUtil.isNotBlank(deployKey))
                .and(App::getPriority).eq(priority)
                .and(App::getUserId).eq(userId);
        // sortField 为运行时变量，无法用 Lambda 引用，需自行保证与 PG 实际列名匹配
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        return queryWrapper;
    }

    /**
     * AI 根据提示词生成 App 名称（失败降级为截取提示词）
     */
    private String generateAppName(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return "未命名应用";
        }
        try {

            String name = aiAppNamingService.generateAppName(userMessage);
            if (StrUtil.isNotBlank(name)) {
                // 去掉首尾空白与可能的引号包裹
                name = name.trim().replace("\"", "").replace("'", "");
                // 模型可能输出代码片段（如 ```html、<!DOCTYPE html）：尝试提取页面标题作为名称，失败再降级
                if (isCodeLike(name)) {
                    String title = extractTitle(name);
                    if (StrUtil.isNotBlank(title)) {
                        log.warn("AI 生成应用名称包含代码，改用提取的页面标题: {}", title);
                        return title.length() > 15 ? title.substring(0, 15) : title;
                    }
                    log.warn("AI 生成应用名称包含代码且无法提取标题，降级为截取提示词");
                    return truncateName(userMessage);
                }
                return name.length() > 15 ? name.substring(0, 15) : name;
            }
            log.warn("AI 生成应用名称为空，降级为截取提示词");
        } catch (Exception e) {
            log.error("AI 生成应用名称失败，降级为截取提示词", e);
        }
        return truncateName(userMessage);
    }

    /**
     * 判断 AI 输出是否为代码片段（而非应用名称）
     */
    private boolean isCodeLike(String name) {
        String trimmed = name.trim().toLowerCase();
        return trimmed.startsWith("```")
                || trimmed.startsWith("<!doctype")
                || trimmed.startsWith("<html")
                || trimmed.contains("```html")
                || trimmed.contains("```css")
                || trimmed.contains("```js");
    }

    /** 过滤无意义的通用页面标题 */
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Set<String> GENERIC_TITLES = Set.of("vite + vue", "vue", "index", "untitled", "document");

    /**
     * 从模型输出的 HTML 代码中提取 <title>; 作为应用名称（模型输出代码时的兜底）
     */
    private String extractTitle(String content) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String title = matcher.group(1).trim();
        if (StrUtil.isBlank(title) || GENERIC_TITLES.contains(title.toLowerCase())) {
            return null;
        }
        return title;
    }

    /**
     * 兜底名称：截取提示词前 15 位（AI 失败或创建时的占位名）
     */
    private String truncateName(String prompt) {
        if (StrUtil.isBlank(prompt)) {
            return "未命名应用";
        }
        return prompt.substring(0, Math.min(prompt.length(), 15));
    }

    /**
     * 仅当原始值为两态存储标识（oss: / local:）时返回其本身，否则返回 null（存量 URL 数据不向下游透传标识）
     */
    private String storageKey(String cover) {
        if (StrUtil.isBlank(cover)) {
            return null;
        }
        if (cover.startsWith("oss:") || cover.startsWith("local:")) {
            return cover;
        }
        return null;
    }
}
