package top.littlewin.codespark.core.stream;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.AiThinkingMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.ai.model.message.StreamMessageTypeEnum;
import top.littlewin.codespark.ai.model.message.ToolExecutedMessage;
import top.littlewin.codespark.ai.model.message.ToolRequestMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TokenStreamMessageEmitter 单元测试
 * 通过 mock TokenStream 捕获注册的回调，模拟模型事件流，验证强类型事件映射。
 */
class TokenStreamMessageEmitterTest {

    private final TokenStreamMessageEmitter emitter = new TokenStreamMessageEmitter();

    /** mock TokenStream 并捕获其注册的全部回调 */
    private record Handlers(
            AtomicReference<Consumer<PartialThinking>> thinking,
            AtomicReference<Consumer<String>> partialResponse,
            AtomicReference<Consumer<BeforeToolExecution>> beforeToolExecution,
            AtomicReference<Consumer<ToolExecution>> toolExecuted,
            AtomicReference<Consumer<Object>> completeResponse,
            AtomicReference<Consumer<Throwable>> onError) {
    }

    private Handlers mockTokenStream(TokenStream tokenStream) {
        Handlers handlers = new Handlers(
                new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), new AtomicReference<>(),
                new AtomicReference<>(), new AtomicReference<>());

        when(tokenStream.onPartialThinking(any())).thenAnswer(inv -> {
            handlers.thinking().set(inv.getArgument(0));
            return tokenStream;
        });
        when(tokenStream.onPartialResponse(any())).thenAnswer(inv -> {
            handlers.partialResponse().set(inv.getArgument(0));
            return tokenStream;
        });
        when(tokenStream.beforeToolExecution(any())).thenAnswer(inv -> {
            handlers.beforeToolExecution().set(inv.getArgument(0));
            return tokenStream;
        });
        when(tokenStream.onToolExecuted(any())).thenAnswer(inv -> {
            handlers.toolExecuted().set(inv.getArgument(0));
            return tokenStream;
        });
        when(tokenStream.onCompleteResponse(any())).thenAnswer(inv -> {
            handlers.completeResponse().set(inv.getArgument(0));
            return tokenStream;
        });
        when(tokenStream.onError(any())).thenAnswer(inv -> {
            handlers.onError().set(inv.getArgument(0));
            return tokenStream;
        });
        return handlers;
    }

    /** 订阅事件流（同步注册回调），返回收集器 */
    private record Subscription(List<StreamMessage> received,
                                AtomicReference<Throwable> error,
                                CountDownLatch latch) {
    }

    private Subscription subscribe(TokenStream tokenStream) {
        List<StreamMessage> received = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        emitter.emit(tokenStream).subscribe(
                received::add,
                e -> {
                    error.set(e);
                    latch.countDown();
                },
                latch::countDown);
        return new Subscription(received, error, latch);
    }

    @Test
    void emit_mapsThinkingAndResponseThenCompletes() throws Exception {
        TokenStream tokenStream = mock(TokenStream.class);
        Handlers handlers = mockTokenStream(tokenStream);

        Subscription sub = subscribe(tokenStream);

        // 订阅后回调已注册（start() 已触发），模拟模型事件顺序
        handlers.thinking().get().accept(new PartialThinking("正在分析需求"));
        handlers.thinking().get().accept(new PartialThinking("，拆解页面结构..."));
        handlers.partialResponse().get().accept("<!DOCTYPE html>");
        handlers.completeResponse().get().accept(null);

        assertTrue(sub.latch().await(5, TimeUnit.SECONDS), "流应在 onCompleteResponse 后完成");
        assertNull(sub.error().get());

        List<StreamMessage> received = sub.received();
        assertEquals(3, received.size());

        assertInstanceOf(AiThinkingMessage.class, received.get(0));
        assertEquals(StreamMessageTypeEnum.AI_THINKING.getValue(), received.get(0).getType());
        assertEquals("正在分析需求", ((AiThinkingMessage) received.get(0)).getData());

        assertInstanceOf(AiThinkingMessage.class, received.get(1));
        assertEquals("，拆解页面结构...", ((AiThinkingMessage) received.get(1)).getData());

        assertInstanceOf(AiResponseMessage.class, received.get(2));
        assertEquals(StreamMessageTypeEnum.AI_RESPONSE.getValue(), received.get(2).getType());
        assertEquals("<!DOCTYPE html>", ((AiResponseMessage) received.get(2)).getData());

        // 订阅时才 start()，且恰好一次
        verify(tokenStream).start();
    }

    @Test
    void emit_mapsToolEvents() throws Exception {
        TokenStream tokenStream = mock(TokenStream.class);
        Handlers handlers = mockTokenStream(tokenStream);

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1")
                .name("writeFile")
                .arguments("{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template></template>\"}")
                .build();

        Subscription sub = subscribe(tokenStream);

        handlers.beforeToolExecution().get()
                .accept(BeforeToolExecution.builder()
                        .request(request)
                        .invocationContext(mock(InvocationContext.class))
                        .build());
        handlers.toolExecuted().get()
                .accept(ToolExecution.builder()
                        .request(request)
                        .result("ok")
                        .invocationContext(mock(InvocationContext.class))
                        .build());
        handlers.completeResponse().get().accept(null);

        assertTrue(sub.latch().await(5, TimeUnit.SECONDS));
        List<StreamMessage> received = sub.received();
        assertEquals(2, received.size());

        assertInstanceOf(ToolRequestMessage.class, received.get(0));
        assertEquals(StreamMessageTypeEnum.TOOL_REQUEST.getValue(), received.get(0).getType());
        assertEquals("tool-1", ((ToolRequestMessage) received.get(0)).getId());
        assertEquals("writeFile", ((ToolRequestMessage) received.get(0)).getName());

        assertInstanceOf(ToolExecutedMessage.class, received.get(1));
        assertEquals(StreamMessageTypeEnum.TOOL_EXECUTED.getValue(), received.get(1).getType());
        assertEquals("tool-1", ((ToolExecutedMessage) received.get(1)).getId());
        assertEquals("ok", ((ToolExecutedMessage) received.get(1)).getResult());
    }

    @Test
    void emit_propagatesError() throws Exception {
        TokenStream tokenStream = mock(TokenStream.class);
        Handlers handlers = mockTokenStream(tokenStream);

        Subscription sub = subscribe(tokenStream);

        handlers.onError().get().accept(new RuntimeException("boom"));

        assertTrue(sub.latch().await(5, TimeUnit.SECONDS));
        assertNotNull(sub.error().get());
        assertTrue(sub.error().get() instanceof RuntimeException);
        assertEquals("boom", sub.error().get().getMessage());
    }
}
