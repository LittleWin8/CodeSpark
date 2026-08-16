package top.littlewin.codespark.core;

import cn.hutool.json.JSONUtil;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.AICodeGeneratorService;
import top.littlewin.codespark.ai.AICodeGeneratorServiceFactory;
import top.littlewin.codespark.ai.model.HTMLCodeResult;
import top.littlewin.codespark.ai.model.MultiFileCodeResult;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.ToolExecutedMessage;
import top.littlewin.codespark.ai.model.message.ToolRequestMessage;
import top.littlewin.codespark.core.parser.CodeParserExecutor;
import top.littlewin.codespark.core.saver.CodeFileSaverExecutor;
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
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码文件类型
     * @param appId 应用 ID
     * @return 保存目录
     */
    @Deprecated(forRemoval = true)
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType, Long appId){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(appId, codeGenType);

        return switch (codeGenType){
            case HTML -> {
                HTMLCodeResult result = aiCodeGeneratorService.generateHTMLCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenType, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenType, appId);
            }
            default -> {
                String errorMessage = "不支持生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码(流式)
     *
     * @param userMessage 用户提示词
     * @param codeGenType 代码文件类型
     * @param appId 应用 ID
     * @return 保存目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType, Long appId){

        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "生成类型不能为空");

        AICodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(appId, codeGenType);

        Flux<String> codeStream = switch (codeGenType){
            case HTML -> aiCodeGeneratorService.generateHTMLCodeStream(userMessage);
            case MULTI_FILE -> aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream);
            }
            default -> {
                String errorMessage = "不支持生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };

        // vue 模式通过【文件写入工具】调用直接落盘，无需再走解析-保存流程
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            return codeStream;
        }
        return processCodeStream(codeStream, codeGenType, appId);
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象（由 AI Service 方法生成，如 generateVueProjectCodeStream）
     * @return Flux&lt;String&gt; 流式响应（每项为一条 JSON 消息）
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            // AI 文本分片：模型每流式输出一段文本触发一次，包装为 ai_response 消息
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    // 工具执行前：每次工具调用触发一次，request() 返回完整 ToolExecutionRequest，
                    // 包装为 tool_request 消息，前端可据此展示"正在写入 xxx"
                    .beforeToolExecution(beforeToolExecution -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(beforeToolExecution.request());
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    // 工具执行后：携带请求 + 执行结果，包装为 tool_executed 消息
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    // 最终响应：整个生成流程（含所有工具轮次）结束后触发，结束 Flux 流
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    // 出错时终止流并向订阅方传播异常
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    // 所有回调注册完毕后，必须调用 start() 才开始消费 TokenStream
                    .start();
        });
    }



    /**
     * 代码流处理方法
     *
     * @param codeStream 代码流
     * @param codeGenType 代码生成类别
     * @param appId 应用 ID
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId){
        StringBuilder codeBulider = new StringBuilder();
        return codeStream.doOnNext(chunk ->{
            codeBulider.append(chunk);
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
