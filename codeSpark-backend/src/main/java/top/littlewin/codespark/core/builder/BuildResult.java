package top.littlewin.codespark.core.builder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 构建结果（成功与否 + 失败原因）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildResult {

    private boolean success;

    private String message;

    public static BuildResult success() {
        return new BuildResult(true, null);
    }

    public static BuildResult fail(String message) {
        return new BuildResult(false, message);
    }
}