package top.littlewin.codespark.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 应用名称生成结果
 */
@Data
@Description("生成应用名称的结果")
public class AppNameResult {

    // 应用名称
    @Description("应用名称")
    private String appName;
}
