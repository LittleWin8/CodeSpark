package top.littlewin.codespark.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 多文件代码结果
 */
@Data
@Description("生成多个代码文件的结果")
public class MultiFileCodeResult {

    // HTML 代码
    @Description("HTML代码")
    private String htmlCode;

    // CSS 代码
    @Description("CSS代码")
    private String cssCode;

    // JS 代码
    @Description("JS代码")
    private String jsCode;

    // 其余描述
    @Description("生成代码描述")
    private String description;
}
