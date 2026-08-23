package top.littlewin.codespark.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface AICodeGeneratorService {

    /**
     * 流式生成单 HTML 文件
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果（含 ai_thinking / ai_response 事件，由 TokenStreamMessageEmitter 统一转换）
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    TokenStream generateHTMLCodeStream(String userMessage);

    /**
     * 流式生成多文件
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果（含 ai_thinking / ai_response 事件，由 TokenStreamMessageEmitter 统一转换）
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    TokenStream generateMultiFileCodeStream(String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

}
