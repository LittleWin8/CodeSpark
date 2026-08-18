package top.littlewin.codespark.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.DisplayTextMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.ai.model.message.StreamMessageTypeEnum;
import top.littlewin.codespark.ai.model.message.ToolExecutedMessage;
import top.littlewin.codespark.ai.model.message.ToolRequestMessage;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.ChatHistoryMessageTypeEnum;
import top.littlewin.codespark.service.ChatHistoryService;

/**
 * 强类型流式消息处理器（统一处理 HTML / MULTI_FILE / VUE_PROJECT 三种模式）
 * <p>
 * 职责：
 * 1. 展示事件：thinking / 正文原样透传；工具事件（tool_request / tool_executed）以强类型透传，
 *    由 Controller 转为 SSE 命名事件，前端渲染为"正在写入 / 已写入"状态卡片；
 * 2. 维护聊天历史：只累积正文与工具执行块（thinking 是过程状态不落历史，历史会回放进
 *    对话记忆作为上下文，把思考当正文回喂会污染对话）。工具执行块仍以文本格式持久化，
 *    与既有历史数据兼容，前端按同一格式解析渲染。
 * <p>
 * 历史累积 = ai_response 文本 + tool_executed 文本块（tool_request 不进历史）。
 * <p>
 * 流完成后的生成物动作（HTML/MULTI 解析落盘、VUE 异步构建）由
 * {@link top.littlewin.codespark.core.AICodeGeneratorFacade} 触发，本类不感知生成模式。
 */
@Slf4j
@Component
public class StreamMessageHandler {

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 处理强类型事件流
     *
     * @param eventStream 原始事件流（TokenStreamMessageEmitter 产出）
     * @param appId       应用 ID
     * @param loginUser   登录用户
     * @return 展示事件流（ai_thinking 保持原类型；工具事件保持原类型；其余携带展示文本）
     */
    public Flux<StreamMessage> handle(Flux<StreamMessage> eventStream,
                                      long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式（不含 thinking）
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        return eventStream
                .map(chunk -> handleMessageChunk(chunk, chatHistoryStringBuilder))
                .filter(msg -> (msg instanceof DisplayTextMessage display
                        && StrUtil.isNotEmpty(display.getData()))
                        || msg instanceof ToolRequestMessage
                        || msg instanceof ToolExecutedMessage) // 工具事件以强类型透传
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 处理单个强类型消息
     * <p>
     * 工具事件不再压成文本标记（原 [选择工具] / [工具调用] 文本），以强类型透传给前端
     * 渲染为状态卡片；聊天历史仍以文本块格式持久化（保持与历史数据兼容）。
     */
    private StreamMessage handleMessageChunk(StreamMessage msg, StringBuilder chatHistoryStringBuilder) {
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(msg.getType());
        return switch (typeEnum) {
            case AI_THINKING -> msg; // 思考过程：只透传展示，不进历史

            case AI_RESPONSE -> {
                String data = ((AiResponseMessage) msg).getData();
                chatHistoryStringBuilder.append(data);
                yield msg;
            }

            case TOOL_REQUEST -> msg; // 强类型透传（前端展示"正在写入"状态），不进历史

            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = (ToolExecutedMessage) msg;
                // 历史仍以文本块格式持久化（与既有历史数据兼容，前端按同一格式解析渲染）
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String relativeFilePath = jsonObject.getStr("relativeFilePath");
                String suffix = FileUtil.getSuffix(relativeFilePath);
                String content = jsonObject.getStr("content");
                String result = String.format("""
                        [工具调用] 写入文件 %s
                        ```%s
                        %s
                        ```
                        """, relativeFilePath, suffix, content);
                chatHistoryStringBuilder.append(String.format("\n\n%s\n\n", result));
                yield msg; // 强类型透传（前端渲染"已写入"卡片）
            }

            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                yield new AiResponseMessage("");
            }
        };
    }
}
