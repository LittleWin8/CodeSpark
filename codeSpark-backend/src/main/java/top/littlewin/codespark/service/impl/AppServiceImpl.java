package top.littlewin.codespark.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.core.AICodeGeneratorFacade;
import top.littlewin.codespark.core.builder.VueProjectBulider;
import top.littlewin.codespark.core.handler.StreamMessageHandler;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.manager.OssManager;
import top.littlewin.codespark.model.dto.app.AppAddRequest;
import top.littlewin.codespark.model.dto.app.AppQueryRequest;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.mapper.AppMapper;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.ChatHistoryMessageTypeEnum;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;
import top.littlewin.codespark.model.vo.AppVO;
import top.littlewin.codespark.model.vo.UserVO;
import top.littlewin.codespark.service.AppService;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.service.ChatHistoryService;
import top.littlewin.codespark.service.ScreenshotService;
import top.littlewin.codespark.service.UserService;
import top.littlewin.codespark.utils.AppUrlUtil;

import java.io.File;
import java.io.Serializable;
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
    private OssManager ossManager;

    /** 自注入代理：createApp 内部调用 @Async 方法时，需通过代理走异步线程池 */
    @Lazy
    @Resource
    private AppService self;

    /**
     * AI 生成失败时的兜底提示（写入聊天历史，避免空消息）
     */
    private static final String GENERATE_FAILED_MESSAGE = "应用生成失败，请重试~";

    /** 封面存储标识前缀：oss: + 对象 key，表示该封面存 OSS，输出层需动态签名 */
    private static final String OSS_COVER_PREFIX = "oss:";

    /**
     * 解析封面为前端可访问的 URL：
     * - oss: 标识 → 用 OssManager 生成当前有效的预签名 URL（私有桶可访问、永不过期）；
     * - 本地 URL（/api/file/cover/...）→ 原样返回；
     * - 空/签名失败 → 返回 null（前端自动落到占位符兜底）。
     */
    private String resolveCoverUrl(String cover) {
        if (StrUtil.isBlank(cover)) {
            return null;
        }
        if (cover.startsWith(OSS_COVER_PREFIX)) {
            return ossManager.buildPresignedUrl(cover.substring(OSS_COVER_PREFIX.length()));
        }
        return cover;
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "EMPTY_INIT_PROMPT");

        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());

        // 先使用截取名称快速落库，避免等待 AI 生成名称阻塞创建请求
        app.setAppName(this.truncateName(initPrompt));

        // 校验生成类型,NAMING类型不可
        String codeGenType = appAddRequest.getCodeGenType();
        ThrowUtils.throwIf(CodeGenTypeEnum.getEnumByValue(codeGenType) == null &&
                CodeGenTypeEnum.NAMING.getValue().equals(codeGenType), ErrorCode.PARAMS_ERROR, "INVALID_CODE_GEN_TYPE");
        app.setCodeGenType(codeGenType);

        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 异步生成应用名称并更新（不阻塞响应，名称生成后前端刷新可见）
        self.updateAppNameAsync(app.getId(), initPrompt);

        return app.getId();
    }

    /**
     * 异步生成并更新应用名称（不阻塞创建请求）
     */
    @Async
    @Override
    public void updateAppNameAsync(Long appId, String userMessage) {
        try {
            String name = this.generateAppName(userMessage);
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setAppName(name);
            this.updateById(updateApp);
            log.info("异步更新应用名称成功: appId={}, appName={}", appId, name);
        } catch (Exception e) {
            log.error("异步更新应用名称失败: appId={}", appId, e);
        }
    }

    @Override
    public Flux<StreamMessage> chatToGenCode(Long appId, String message, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "INVALID_APP_ID");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "EMPTY_CHAT_MESSAGE");

        // 2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "APP_NOT_FOUND");

        // 3.权限校验，仅本人可以和 AI 对话
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        // 4. 应用代码类型
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null, ErrorCode.SYSTEM_ERROR, "INVALID_CODE_GEN_TYPE");

        // 5. 保存用户消息
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());

        // 6. 调用 AI 生成代码
        Flux<StreamMessage> contentStream =  aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenType, appId);

        // 7. 渲染展示文本 + 保存 AI 响应结果（生成物落盘/构建已由 Facade 在流完成时触发）
        Flux<StreamMessage> handledStream = streamMessageHandler.handle(contentStream, appId, loginUser);

        // 8. 非 VUE 模式：流完成后（文件已落盘）异步截图设为应用封面；
        //    VUE 需等构建完成（dist 生成），由 Facade 的构建成功回调触发
        if (codeGenType != CodeGenTypeEnum.VUE_PROJECT) {
            handledStream = handledStream.doOnComplete(() ->
                    generateAppScreenshotAsync(appId, AppUrlUtil.buildPreviewUrl(codeGenType, appId)));
        }
        return handledStream;
    }

    @Override
    public String deployApp(Long appId, User loginUser) {

        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "INVALID_APP_ID");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "APP_NOT_FOUND");

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
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "APP_CODE_NOT_GENERATED");

        // 7. 复制文件到部署目录，为保证vue能够顺利部署，需要在构建一遍
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT){

            // Vue 项目构建
            boolean buildSuccess =vueProjectBulider.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");

            // 检查 dist目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建我完成，但是炳文生成 dist 目录");

            // 构建成功，将源目录设置为 dist 目录
            sourceDir = distDir;
        }

        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        }catch (Exception e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "APP_DEPLOY_FAILED");
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
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "APP_DEPLOY_UPDATE_FAILED");
        }

        // 10. 返回可访问的 URL
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
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
                ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
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
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, "APP_NOT_FOUND");

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

        // 4. 删除封面目录：app_cover/{appId}
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
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "APP_DELETE_FAILED");
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
        // 封面动态解析：OSS 标识→现场签名（私有桶可访问且永不过期）；本地 URL 原样返回
        appVO.setCover(resolveCoverUrl(appVO.getCover()));
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
            // 封面动态解析：OSS 标识→现场签名；本地 URL 原样返回
            appVO.setCover(resolveCoverUrl(appVO.getCover()));
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

            String name = aiCodeGeneratorFacade.generateAppName(userMessage, CodeGenTypeEnum.NAMING);
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
     * 从模型输出的 HTML 代码中提取 &lt;title&gt; 作为应用名称（模型输出代码时的兜底）
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
}
