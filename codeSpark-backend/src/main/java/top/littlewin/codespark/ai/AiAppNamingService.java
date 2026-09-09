package top.littlewin.codespark.ai;

import dev.langchain4j.service.SystemMessage;

/**
 * AI 应用命名服务
 * <p>
 * 独立于代码生成服务：仅返回简短应用名称（纯文本、无工具、无记忆），
 * 使用轻量模型（deepseek-chat），与路由服务共用同一模型分级策略。
 */
public interface AiAppNamingService {

    /**
     * 根据用户需求生成应用名称
     *
     * @param userMessage 用户输入的提示词
     * @return 应用名称
     */
    @SystemMessage(fromResource = "prompt/codegen-app-naming-system-prompt.txt")
    String generateAppName(String userMessage);
}
