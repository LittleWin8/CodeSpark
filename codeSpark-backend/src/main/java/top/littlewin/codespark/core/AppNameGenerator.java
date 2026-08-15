package top.littlewin.codespark.core;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.service.AppService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 应用名称生成器：AI 生成 + 截取兜底
 * <p>
 * 命名任务使用独立的轻量模型（非推理模型 + 小 token 预算），避免与代码生成共用大模型导致响应慢
 */
@Slf4j
@Service
public class AppNameGenerator {

    /** 应用名称最大长度：AI 名称超长时截断，AI 失败时也按此长度截取提示词兜底 */
    public static final int MAX_NAME_LENGTH = 20;

    /** 命名提示词资源路径 */
    private static final String NAMER_PROMPT_RESOURCE = "prompt/app_namer.md";

    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String apiKey;

    @Value("${codespark.ai.app-name.model-name:deepseek-chat}")
    private String modelName;

    @Value("${codespark.ai.app-name.max-tokens:128}")
    private int maxTokens;

    @Resource
    private AppService appService;

    /** 轻量命名模型实例（非 Spring Bean，仅内部使用，不影响 langchain4j 默认模型自动配置） */
    private OpenAiChatModel appNameModel;

    private String systemPrompt;

    @PostConstruct
    void init() {
        appNameModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                // 注意：不加 responseFormat("json_object")。
                // DeepSeek 要求 json_object 时提示词必须含 "json" 字样，且命名只需返回纯文本名称，无需 JSON
                .build();
        systemPrompt = loadPrompt(NAMER_PROMPT_RESOURCE);
        log.info("App 名称生成器已初始化: model={}, maxTokens={}", modelName, maxTokens);
    }

    /**
     * 兜底名称：截取提示词前 MAX_NAME_LENGTH 位（AI 失败或创建时的占位名）
     */
    public static String truncateName(String prompt) {
        if (StrUtil.isBlank(prompt)) {
            return "未命名应用";
        }
        return prompt.substring(0, Math.min(prompt.length(), MAX_NAME_LENGTH));
    }

    /**
     * AI 根据提示词自动生成 App 名称
     *
     * @param userMessage 用户提示词
     * @return 应用名称（AI 生成失败时降级为截取提示词）
     */
    public String genAppName(String userMessage) {
        if (StrUtil.isBlank(userMessage)) {
            return "未命名应用";
        }
        try {
            AiMessage aiMessage = appNameModel.chat(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
            )).aiMessage();
            String name = aiMessage != null ? aiMessage.text() : null;
            if (StrUtil.isNotBlank(name)) {
                // 去掉首尾空白与可能的引号包裹
                name = name.trim().replace("\"", "").replace("'", "");
                // 限制名称长度，防止 AI 输出超长内容
                return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
            }
            log.warn("AI 生成应用名称为空，降级为截取提示词");
        } catch (Exception e) {
            log.error("AI 生成应用名称失败，降级为截取提示词", e);
        }
        return truncateName(userMessage);
    }

    private String loadPrompt(String resource) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("未找到提示词资源: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取提示词资源失败: " + resource, e);
        }
    }

    /**
     * 异步生成并更新应用名称（不阻塞创建请求）
     *
     * @param appId       应用 ID
     * @param userMessage 用户提示词
     */
    @Async
    public void updateAppNameAsync(Long appId, String userMessage) {
        try {
            String name = genAppName(userMessage);
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setAppName(name);
            appService.updateById(updateApp);
            log.info("异步更新应用名称成功: appId={}, appName={}", appId, name);
        } catch (Exception e) {
            log.error("异步更新应用名称失败: appId={}", appId, e);
        }
    }
}
