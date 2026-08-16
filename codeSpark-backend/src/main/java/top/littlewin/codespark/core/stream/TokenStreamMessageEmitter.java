package top.littlewin.codespark.core.stream;

import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.AiThinkingMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.ai.model.message.ToolExecutedMessage;
import top.littlewin.codespark.ai.model.message.ToolRequestMessage;

/**
 * TokenStream 的唯一适配点：把回调式 TokenStream 转换为强类型事件流。
 *
 * 事件顺序即模型输出顺序（thinking → content；工具轮内 thinking → tool_request → tool_executed → ...）。
 *
 * 约束：返回的 Flux 是冷流，start() 在订阅时触发，同一实例【只能订阅一次】
 * （重复订阅会重复 start() → 重复调用模型）。SSE 链路为单订阅，满足该约束。
 */
@Slf4j
@Component
public class TokenStreamMessageEmitter {

    /**
     * 将 TokenStream 转换为 Flux<StreamMessage> 强类型事件流
     *
     * @param tokenStream AI Service 方法返回的 TokenStream（如 generateVueProjectCodeStream）
     * @return 事件流：ai_thinking / ai_response / tool_request / tool_executed
     */
    public Flux<StreamMessage> emit(TokenStream tokenStream) {
        return Flux.create(sink -> tokenStream
                // 模型推理分片：包装为 ai_thinking 消息，前端据此展示"正在思考"状态
                .onPartialThinking(partialThinking ->
                        sink.next(new AiThinkingMessage(partialThinking.text())))
                // AI 正文分片：模型每流式输出一段文本触发一次
                .onPartialResponse(partialResponse ->
                        sink.next(new AiResponseMessage(partialResponse)))
                // 工具执行前：每次工具调用触发一次，request() 返回完整 ToolExecutionRequest
                .beforeToolExecution(beforeToolExecution ->
                        sink.next(new ToolRequestMessage(beforeToolExecution.request())))
                // 工具执行后：携带请求 + 执行结果
                .onToolExecuted(toolExecution ->
                        sink.next(new ToolExecutedMessage(toolExecution)))
                // 最终响应：整个生成流程（含所有工具轮次）结束后触发，结束事件流
                .onCompleteResponse(response -> sink.complete())
                // 出错时终止流并向订阅方传播异常
                .onError(error -> {
                    log.error("AI 流式生成失败", error);
                    sink.error(error);
                })
                // 所有回调注册完毕后，必须调用 start() 才开始消费 TokenStream
                .start());
    }
}
