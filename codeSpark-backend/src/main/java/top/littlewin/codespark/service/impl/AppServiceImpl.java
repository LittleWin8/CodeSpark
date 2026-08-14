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
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.core.AICodeGeneratorFacade;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
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
import top.littlewin.codespark.service.UserService;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * AI 生成失败时的兜底提示（写入聊天历史，避免空消息）
     */
    private static final String GENERATE_FAILED_MESSAGE = "应用生成失败，请重试~";

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
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
        Flux<String> contentStream =  aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenType, appId);

        // 7. 保存 AI 响应结果
        StringBuilder aiMessageBuilder = new StringBuilder();
        return contentStream
                .doOnNext(aiMessageBuilder::append)
                .doOnComplete(() -> {
                    String aiMessage = aiMessageBuilder.toString();
                    if (StrUtil.isBlank(aiMessage)){
                        aiMessage = GENERATE_FAILED_MESSAGE;
                    }
                    // AI 未输出有效内容时，也按失败处理落库一条提示（避免空消息入库）
                    chatHistoryService.addChatMessage(appId, aiMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> chatHistoryService.addChatMessage(appId, GENERATE_FAILED_MESSAGE, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId()));
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

        // 7. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        }catch (Exception e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "APP_DEPLOY_FAILED");
        }

        // 8. 更新数据库
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

        // 9. 返回可访问的 URL
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
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
}
