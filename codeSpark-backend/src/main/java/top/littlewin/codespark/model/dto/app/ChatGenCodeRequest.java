package top.littlewin.codespark.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 对话生成应用请求
 */
@Data
public class ChatGenCodeRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 用户提示词
     */
    private String message;

    private static final long serialVersionUID = 1L;
}