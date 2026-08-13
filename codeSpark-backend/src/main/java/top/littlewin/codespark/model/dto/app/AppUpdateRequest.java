package top.littlewin.codespark.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新应用请求
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面（在线 URL 或上传后的地址）
     */
    private String cover;

    private static final long serialVersionUID = 1L;
}
