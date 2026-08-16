package top.littlewin.codespark.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 思考消息（推理模型的流式推理过程）
 * 仅用于前端实时展示"当前状态"，不写入聊天历史与对话记忆
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class AiThinkingMessage extends StreamMessage implements DisplayTextMessage {

    private String data;

    public AiThinkingMessage(String data) {
        super(StreamMessageTypeEnum.AI_THINKING.getValue());
        this.data = data;
    }
}
