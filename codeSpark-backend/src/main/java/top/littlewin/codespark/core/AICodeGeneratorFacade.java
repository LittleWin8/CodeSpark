package top.littlewin.codespark.core;

import jakarta.annotation.Resource;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.AICodeGeneratorService;
import top.littlewin.codespark.ai.AICodeGeneratorServiceFactory;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.core.builder.VueProjectBulider;
import top.littlewin.codespark.core.parser.CodeParserExecutor;
import top.littlewin.codespark.core.saver.CodeFileSaverExecutor;
import top.littlewin.codespark.core.stream.TokenStreamMessageEmitter;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.enums.ChatStageEnum;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * AI 代码生成门面类：组合代码生成和保存功能。
 * 应用命名由独立的 AppNamingService 负责（轻量模型），不走本门面。
 */
@Slf4j
@Service
public class AICodeGeneratorFacade {

    @Resource
    private AICodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private TokenStreamMessageEmitter tokenStreamMessageEmitter;

    @Resource
    private VueProjectBulider vueProjectBulider;


    /**
     * 统一入口：根据类型生成并保存代码(流式)
     * 三种模式统一返回 TokenStream，经 TokenStreamMessageEmitter 转换为强类型事件流
     * （ai_thinking / ai_response / tool_request / tool_executed），下游按类型分流处理。
     * 流完成后的生成物动作统一在本门面触发：三种模式经 attachCompletionAction 编排
     * （HTML/MULTI_FILE 解析落盘；VUE_PROJECT 触发 VueProjectBulider 异步构建）。
     * VUE 模式按对话阶段（创建/修改）路由到不同的系统提示词（同一服务实例、共享会话记忆）。
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码文件类型
     * @param appId 应用 ID
     * @param chatStage 对话阶段（创建 / 修改），决定 VUE 模式使用的系统提示词
     * @return 强类型流式事件
     */
    public Flux<StreamMessage> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType, Long appId, ChatStageEnum chatStage){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, ErrorMessage.CODE_GEN_TYPE_REQUIRED);

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(appId, codeGenType);

        Flux<StreamMessage> eventStream = switch (codeGenType){
            case HTML -> tokenStreamMessageEmitter.emit(
                    aiCodeGeneratorService.generateHTMLCodeStream(userMessage));
            case MULTI_FILE -> tokenStreamMessageEmitter.emit(
                    aiCodeGeneratorService.generateMultiFileCodeStream(userMessage));
            case VUE_PROJECT -> tokenStreamMessageEmitter.emit(
                    chatStage == ChatStageEnum.CREATE
                            ? aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage)
                            : aiCodeGeneratorService.modifyVueProjectCodeStream(appId, userMessage));
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, ErrorMessage.UNSUPPORTED_CODE_GEN_TYPE);
        };

        // 流完成后的生成物动作统一在此编排（HTML/MULTI 解析落盘；VUE 异步构建）
        return attachCompletionAction(eventStream, codeGenType, appId);
    }

    /**
     * 挂载流完成后的生成物动作（类型 → 动作 的显式映射）
     * - VUE_PROJECT：落盘已由文件写入工具实时完成，流完成后只需异步构建（npm install + build）；
     * - HTML / MULTI_FILE：只累积 ai_response 正文事件（ai_thinking 推理内容不混入生成的代码文件），
     *   流完成后解析并保存代码；
     * - 其余类型：不属于本流程，显式拒绝。
     *
     * @param eventStream 强类型事件流
     * @param codeGenType 代码生成类别
     * @param appId 应用 ID
     * @return 透传的事件流（side-effect 落盘/构建）
     */
    private Flux<StreamMessage> attachCompletionAction(Flux<StreamMessage> eventStream, CodeGenTypeEnum codeGenType, Long appId) {
        return switch (codeGenType) {
            case VUE_PROJECT -> attachVueBuild(eventStream, appId);
            case HTML, MULTI_FILE -> attachParseAndSave(eventStream, codeGenType, appId);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, ErrorMessage.UNSUPPORTED_CODE_GEN_TYPE);
        };
    }

    /**
     * VUE 模式：落盘由文件写入工具实时完成，流完成后只需异步构建（npm install + build），产出 dist 供部署
     *
     * 构建前同步清空旧 dist：保证 SSE done 到达前端时，预览地址（dist/index.html）处于 404 状态，
     * 前端骨架屏等待，构建完成后重新挂载 iframe 展示新产物，实现修改后自动刷新预览（无需手动刷新）。
     */
    private Flux<StreamMessage> attachVueBuild(Flux<StreamMessage> eventStream, Long appId) {
        return eventStream.doOnComplete(() -> {
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_" + appId;
            // 清理旧 dist，避免构建期间预览命中旧产物（doOnComplete 先于 done 事件执行，无竞态）
            try {
                File distDir = new File(projectPath, "dist");
                if (distDir.exists()) {
                    FileUtil.del(distDir);
                    log.info("清理旧 dist 完成: {}", distDir.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("清理旧 dist 失败: {}", projectPath, e);
            }
            vueProjectBulider.buildProjectAsync(projectPath);
        });
    }

    /**
     * HTML / MULTI_FILE 模式：只累积 ai_response 正文（ai_thinking 推理内容不混入生成的代码文件），
     * 流完成后解析并保存代码
     */
    private Flux<StreamMessage> attachParseAndSave(Flux<StreamMessage> eventStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return eventStream
                .doOnNext(msg -> {
                    if (msg instanceof AiResponseMessage aiResponseMessage) {
                        codeBuilder.append(aiResponseMessage.getData());
                    }
                })
                .doOnComplete(() -> {
                    try {
                        String completeCode = codeBuilder.toString();
                        Object parseResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                        File saveDir = CodeFileSaverExecutor.executeSaver(parseResult, codeGenType, appId);
                        log.info("文件创建成功，保存至" + saveDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败，原因：" + e.getMessage());
                    }
                });
    }
}
