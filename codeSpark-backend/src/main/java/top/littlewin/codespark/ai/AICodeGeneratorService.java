package top.littlewin.codespark.ai;

import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.model.AppNameResult;
import top.littlewin.codespark.ai.model.HTMLCodeResult;
import top.littlewin.codespark.ai.model.MultiFileCodeResult;

public interface AICodeGeneratorService {

    /**
     * 生成单 HTML 文件
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HTMLCodeResult generateHTMLCode(String userMessage);

    /**
     * 生成多文件
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 流式生成单 HTML 文件
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHTMLCodeStream(String userMessage);

    /**
     * 流式生成多文件
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成 App 名称
     * @param userMessage 用户提示词
     * @return App 名称
     */
    @SystemMessage(fromResource = "prompt/app_namer.md")
    AppNameResult generateAppName(String userMessage);

}
