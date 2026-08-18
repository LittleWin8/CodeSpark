package top.littlewin.codespark.core;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;
import top.littlewin.codespark.service.AppService;
import top.littlewin.codespark.utils.AppUrlUtil;

import java.io.File;

/**
 * AI 代码生成门面类，组合代码生成和保存功能，并封装 App 命名
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

    @Lazy
    @Resource
    private AppService appService;


    /**
     * 根据 App 的初始提示词生成应用名称
     * @param userMessage 应用初始提示词
     * @param codeGenType 生成类型
     * @return 应用名称
     */
    public String generateAppName(String userMessage, CodeGenTypeEnum codeGenType){

        ThrowUtils.throwIf(codeGenType != CodeGenTypeEnum.NAMING, ErrorCode.PARAMS_ERROR, "只有命名功能才可以调用方法");

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(0, codeGenType);

        return aiCodeGeneratorService.generateAppName(userMessage);
    }

    /**
     * 统一入口：根据类型生成并保存代码(流式)
     * 三种模式统一返回 TokenStream，经 TokenStreamMessageEmitter 转换为强类型事件流
     * （ai_thinking / ai_response / tool_request / tool_executed），下游按类型分流处理。
     * 流完成后的生成物动作统一在本门面触发：三种模式经 attachCompletionAction 编排
     * （HTML/MULTI_FILE 解析落盘；VUE_PROJECT 触发 VueProjectBulider 异步构建）。
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码文件类型
     * @param appId 应用 ID
     * @return 强类型流式事件
     */
    public Flux<StreamMessage> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType, Long appId){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(appId, codeGenType);

        Flux<StreamMessage> eventStream = switch (codeGenType){
            case HTML -> tokenStreamMessageEmitter.emit(
                    aiCodeGeneratorService.generateHTMLCodeStream(userMessage));
            case MULTI_FILE -> tokenStreamMessageEmitter.emit(
                    aiCodeGeneratorService.generateMultiFileCodeStream(userMessage));
            case VUE_PROJECT -> tokenStreamMessageEmitter.emit(
                    aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage));
            default -> {
                String errorMessage = "不支持生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
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
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的生成类型：" + codeGenType.getValue());
        };
    }

    /**
     * VUE 模式：落盘由文件写入工具实时完成，流完成后只需异步构建（npm install + build），产出 dist 供预览/部署
     */
    private Flux<StreamMessage> attachVueBuild(Flux<StreamMessage> eventStream, Long appId) {
        return eventStream.doOnComplete(() -> {
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_" + appId;
            // 构建成功后异步截图设为应用封面（dist 已生成，静态资源可访问）
            vueProjectBulider.buildProjectAsync(projectPath, () ->
                    appService.generateAppScreenshotAsync(appId, AppUrlUtil.buildPreviewUrl(CodeGenTypeEnum.VUE_PROJECT, appId)));
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
