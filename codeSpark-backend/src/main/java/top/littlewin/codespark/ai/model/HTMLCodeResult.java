package top.littlewin.codespark.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * HTML 代码结果
 */
@Data
@Description("生成 HTML 代码文件的结果")
public class HTMLCodeResult {

    // HTML 代码
    @Description("HTML代码")
    private String htmlCode;

    // 除代码之外的描述
    @Description("生成代码描述")
    private String description;
}
