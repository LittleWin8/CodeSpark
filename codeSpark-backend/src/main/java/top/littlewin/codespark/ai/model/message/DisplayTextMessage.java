package top.littlewin.codespark.ai.model.message;

/**
 * 携带前端展示文本的流式消息
 * <p>
 * 展示流（StreamMessageHandler 输出）只会出现两类实现：AiThinkingMessage（思考）与
 * AiResponseMessage（正文/工具标记），Controller 据此提取展示文本。
 */
public interface DisplayTextMessage {

    /**
     * @return 前端展示文本
     */
    String getData();
}
