package top.littlewin.codespark.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用创建请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    private String codeGenType;

    /**
     * 预置示例应用 ID（点击案例时携带；为空或与提示词不匹配则走真实 AI 生成）
     */
    private String presetId;

    private static final long serialVersionUID = 1L;
}
