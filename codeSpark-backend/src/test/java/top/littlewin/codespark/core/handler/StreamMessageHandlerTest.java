package top.littlewin.codespark.core.handler;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.AiThinkingMessage;
import top.littlewin.codespark.ai.model.message.DisplayTextMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.ai.model.message.ToolExecutedMessage;
import top.littlewin.codespark.ai.model.message.ToolRequestMessage;
import top.littlewin.codespark.core.builder.VueProjectBulider;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.ChatHistoryMessageTypeEnum;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;
import top.littlewin.codespark.service.ChatHistoryService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * StreamMessageHandler 单元测试
 * 验证：thinking 透传不进历史、正文累积、工具去重与格式化、VUE 完成构建、错误落历史。
 */
class StreamMessageHandlerTest {

    private final ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    private final VueProjectBulider vueProjectBulider = mock(VueProjectBulider.class);
    private final StreamMessageHandler handler = new StreamMessageHandler();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "chatHistoryService", chatHistoryService);
        ReflectionTestUtils.setField(handler, "vueProjectBulider", vueProjectBulider);
    }

    private User buildUser() {
        User user = new User();
        user.setId(2L);
        return user;
    }

    private ToolExecutionRequest buildRequest(String id, String arguments) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("writeFile")
                .arguments(arguments)
                .build();
    }

    @Test
    void handle_passesThinkingThroughAndExcludesFromHistory() {
        Flux<StreamMessage> result = handler.handle(
                Flux.just(
                        new AiThinkingMessage("分析页面结构"),
                        new AiResponseMessage("正文内容")),
                1L, buildUser(), CodeGenTypeEnum.HTML);

        List<StreamMessage> messages = result.collectList().block();
        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertInstanceOf(AiThinkingMessage.class, messages.get(0));
        assertEquals("分析页面结构", ((DisplayTextMessage) messages.get(0)).getData());
        assertInstanceOf(AiResponseMessage.class, messages.get(1));
        assertEquals("正文内容", ((DisplayTextMessage) messages.get(1)).getData());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(eq(1L), captor.capture(),
                eq(ChatHistoryMessageTypeEnum.AI.getValue()), eq(2L));
        assertEquals("正文内容", captor.getValue()); // thinking 不进历史
        verifyNoInteractions(vueProjectBulider);    // 非 VUE 模式不触发构建
    }

    @Test
    void handle_passesToolEventsThroughAndAccumulatesHistoryForVue() {
        String arguments = "{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template></template>\"}";
        ToolExecutionRequest request = buildRequest("tool-1", arguments);

        Flux<StreamMessage> result = handler.handle(
                Flux.just(
                        new ToolRequestMessage(request),
                        new ToolExecutedMessage(ToolExecution.builder()
                                .request(request)
                                .result("ok")
                                .invocationContext(mock(InvocationContext.class))
                                .build()),
                        new AiResponseMessage("收尾文本")),
                1L, buildUser(), CodeGenTypeEnum.VUE_PROJECT);

        List<StreamMessage> messages = result.collectList().block();
        assertNotNull(messages);
        assertEquals(3, messages.size(), "工具事件应强类型透传");

        assertInstanceOf(ToolRequestMessage.class, messages.get(0), "tool_request 透传");
        assertInstanceOf(ToolExecutedMessage.class, messages.get(1), "tool_executed 透传");
        assertInstanceOf(AiResponseMessage.class, messages.get(2));
        assertEquals("收尾文本", ((AiResponseMessage) messages.get(2)).getData());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(eq(1L), captor.capture(),
                eq(ChatHistoryMessageTypeEnum.AI.getValue()), eq(2L));
        String history = captor.getValue();
        assertTrue(history.contains("[工具调用] 写入文件 src/App.vue"), "工具执行块进历史");
        assertTrue(history.contains("收尾文本"), "正文进历史");
        assertFalse(history.contains("[选择工具]"), "工具请求不进历史");
        verify(vueProjectBulider).buildProjectAsync(anyString());
    }

    @Test
    void handle_writesFailureMessageOnError() {
        assertThrows(RuntimeException.class, () ->
                handler.handle(Flux.error(new RuntimeException("boom")),
                                1L, buildUser(), CodeGenTypeEnum.HTML)
                        .blockLast());

        verify(chatHistoryService).addChatMessage(eq(1L), eq("AI回复失败: boom"),
                eq(ChatHistoryMessageTypeEnum.AI.getValue()), eq(2L));
    }
}
