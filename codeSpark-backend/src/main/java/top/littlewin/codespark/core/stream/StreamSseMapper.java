package top.littlewin.codespark.core.stream;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import top.littlewin.codespark.ai.model.message.DisplayTextMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.ai.model.message.StreamMessageTypeEnum;
import top.littlewin.codespark.ai.model.message.ToolExecutedMessage;
import top.littlewin.codespark.ai.model.message.ToolRequestMessage;

import java.util.Map;

/**
 * 流式消息 → SSE 事件映射器（AppController 聊天 SSE 出口专用）
 * <p>
 * 将强类型消息流转换为前端可消费的命名事件：
 * <ul>
 *   <li>ai_thinking     → event: thinking       data: {"d": "&lt;思考文本&gt;"}</li>
 *   <li>tool_request    → event: tool_request   data: {"d": {"path": "..."}}</li>
 *   <li>tool_executed   → event: tool_executed  data: {"d": {"path","lang","content"}}</li>
 *   <li>其余（正文）     → 默认 message 事件      data: {"d": "&lt;文本&gt;"}</li>
 * </ul>
 * 从 Controller 中抽离，保持控制层只做参数校验与路由。
 */
@Slf4j
@Component
public class StreamSseMapper {

    /**
     * 将强类型消息转换为 SSE 事件；无法识别路径的工具事件返回 null（由调用方 filter 过滤）
     */
    public ServerSentEvent<String> toServerSentEvent(StreamMessage msg) {
        if (msg instanceof ToolRequestMessage toolRequest) {
            return buildToolEvent(toolRequest.getArguments(), "tool_request", false);
        }
        if (msg instanceof ToolExecutedMessage toolExecuted) {
            return buildToolEvent(toolExecuted.getArguments(), "tool_executed", true);
        }
        String displayData = msg instanceof DisplayTextMessage display ? display.getData() : "";
        ServerSentEvent.Builder<String> builder = ServerSentEvent.<String>builder()
                .data(JSONUtil.toJsonStr(Map.of("d", displayData)));
        if (StreamMessageTypeEnum.AI_THINKING.getValue().equals(msg.getType())) {
            builder.event("thinking");
        }
        return builder.build();
    }

    /**
     * 构建工具事件：从工具参数中提取真实文件路径
     *
     * @param arguments 工具调用参数 JSON（FileWriteTool: {relativeFilePath, content}）
     * @param eventName SSE 命名事件
     * @param withContent 是否携带文件内容（tool_executed）
     */
    private ServerSentEvent<String> buildToolEvent(String arguments, String eventName, boolean withContent) {
        try {
            JSONObject jsonObject = JSONUtil.parseObj(arguments);
            String relativeFilePath = jsonObject.getStr("relativeFilePath");
            if (StrUtil.isBlank(relativeFilePath)) {
                return null;
            }
            JSONObject payload = JSONUtil.createObj().set("path", relativeFilePath);
            if (withContent) {
                payload.set("lang", FileUtil.getSuffix(relativeFilePath));
                payload.set("content", jsonObject.getStr("content"));
            }
            return ServerSentEvent.<String>builder()
                    .event(eventName)
                    .data(JSONUtil.toJsonStr(Map.of("d", payload)))
                    .build();
        } catch (Exception e) {
            log.warn("解析工具调用参数失败，跳过该工具事件: {}", arguments, e);
            return null;
        }
    }
}
