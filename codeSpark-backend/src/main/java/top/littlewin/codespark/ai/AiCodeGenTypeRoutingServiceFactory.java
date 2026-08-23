package top.littlewin.codespark.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI代码生成类型路由服务工厂
 * <p>
 * 路由判断属于简单任务，使用轻量模型（lightChatModel，deepseek-chat，见 AiModelConfig），
 * 与代码生成主模型（deepseek-v4-flash）分级，降低成本与延迟。
 *
 * @author yupi
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    // 轻量模型：仅用于简单任务（类型路由 / 应用命名），见 AiModelConfig.lightChatModel
    @Resource(name = "lightChatModel")
    private ChatModel chatModel;

    /**
     * 创建AI代码生成类型路由服务实例
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }
}
