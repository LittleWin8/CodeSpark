package top.littlewin.codespark.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 应用命名服务工厂
 *
 * 使用轻量模型（lightChatModel，deepseek-chat）；接口无 {@code @MemoryId} 方法，
 * 无需配置记忆，实例无状态，可单例共享。
 */
@Slf4j
@Configuration
public class AppNamingServiceFactory {

    @Resource(name = "lightChatModel")
    private ChatModel chatModel;

    @Bean
    public AppNamingService appNamingService() {
        return AiServices.builder(AppNamingService.class)
                .chatModel(chatModel)
                .build();
    }
}
