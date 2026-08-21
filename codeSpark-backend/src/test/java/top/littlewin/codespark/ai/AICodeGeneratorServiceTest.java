package top.littlewin.codespark.ai;

import jakarta.annotation.Resource;

import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import top.littlewin.codespark.model.enums.CodeGenTypeEnum;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class AICodeGeneratorServiceTest {

    @Resource
    private AICodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    /**
     * 不同 appId 拿到独立记忆的 AI 服务实例（记忆隔离不崩溃 + 各自能完成流式生成）
     */
    @Test
    void testChatMemory() {
        AICodeGeneratorService app1 = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(1L, CodeGenTypeEnum.HTML);
        AICodeGeneratorService app2 = aiCodeGeneratorServiceFactory.getAICodeGeneratorService(2L, CodeGenTypeEnum.HTML);

        String r1 = consume(app1.generateHTMLCodeStream("随便做个工具网站，总代码量不超过 20 行"));
        Assertions.assertTrue(org.springframework.util.StringUtils.hasText(r1));
        String r2 = consume(app1.generateHTMLCodeStream("不要生成代码，只需要告诉我你刚刚做了什么？"));
        Assertions.assertTrue(org.springframework.util.StringUtils.hasText(r2));
        String r3 = consume(app2.generateHTMLCodeStream("随便做个工具网站，总代码量不超过 20 行"));
        Assertions.assertTrue(org.springframework.util.StringUtils.hasText(r3));
        String r4 = consume(app2.generateHTMLCodeStream("不要生成代码，只需要告诉我你刚刚做了什么？"));
        Assertions.assertTrue(org.springframework.util.StringUtils.hasText(r4));
    }

    /**
     * 阻塞消费一条 TokenStream，返回全部正文文本
     */
    private String consume(TokenStream tokenStream) {
        StringBuilder content = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        tokenStream
                .onPartialResponse(content::append)
                .onCompleteResponse(response -> latch.countDown())
                .onError(e -> {
                    error.set(e);
                    latch.countDown();
                })
                .start();
        try {
            boolean finished = latch.await(180, TimeUnit.SECONDS);
            if (!finished) {
                throw new IllegalStateException("AI 流式响应超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 AI 流式响应被中断", e);
        }
        if (error.get() != null) {
            throw new RuntimeException("AI 流式响应失败", error.get());
        }
        return content.toString();
    }

}
