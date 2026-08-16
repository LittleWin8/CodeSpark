package top.littlewin.codespark.core;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.AICodeGeneratorService;
import top.littlewin.codespark.ai.AICodeGeneratorServiceFactory;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.core.parser.CodeParserExecutor;
import top.littlewin.codespark.core.saver.CodeFileSaverExecutor;
import top.littlewin.codespark.core.stream.TokenStreamMessageEmitter;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

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
     * <p>
     * 三种模式统一返回 TokenStream，经 {@link TokenStreamMessageEmitter} 转换为强类型事件流
     * （ai_thinking / ai_response / tool_request / tool_executed），下游按类型分流处理。
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
            case HTML -> tokenStreamMessageEmitter.emit(aiCodeGeneratorService.generateHTMLCodeStream(userMessage));
            case MULTI_FILE -> tokenStreamMessageEmitter.emit(aiCodeGeneratorService.generateMultiFileCodeStream(userMessage));
            case VUE_PROJECT -> tokenStreamMessageEmitter.emit(
                    aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage));
            default -> {
                String errorMessage = "不支持生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };

        // vue 模式通过【文件写入工具】调用直接落盘，无需再走解析-保存流程
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            return eventStream;
        }
        return processCodeStream(eventStream, codeGenType, appId);
    }

    /**
     * 代码流处理方法
     *
     * 只累积 ai_response 正文事件（ai_thinking 推理内容不混入生成的代码文件），
     * 流完成后解析并保存代码。
     *
     * @param eventStream 强类型事件流
     * @param codeGenType 代码生成类别
     * @param appId 应用 ID
     * @return 透传的事件流（side-effect 落盘）
     */
    private Flux<StreamMessage> processCodeStream(Flux<StreamMessage> eventStream, CodeGenTypeEnum codeGenType, Long appId){
        StringBuilder codeBulider = new StringBuilder();
        return eventStream.doOnNext(msg -> {
            if (msg instanceof AiResponseMessage aiResponseMessage) {
                codeBulider.append(aiResponseMessage.getData());
            }
        }).doOnComplete(() ->{
            try {
                String completeCode = codeBulider.toString();
                Object parseResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                File saveDir = CodeFileSaverExecutor.executeSaver(parseResult, codeGenType, appId);
                log.info("文件创建成功，保持至" + saveDir.getAbsolutePath());
            } catch (Exception e){
                log.error("保存失败，原因：" + e.getMessage());
            }
        });
    }


}
