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

    /**
     * 修改 Vue 项目代码（流式）
     * 针对已存在的项目做最小改动，使用独立的修改专用系统提示词
     *
     * @param userMessage 用户消息（可能附带选中的页面元素信息）
     * @return 修改过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-modify-system-prompt.txt")
    TokenStream modifyVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

}
