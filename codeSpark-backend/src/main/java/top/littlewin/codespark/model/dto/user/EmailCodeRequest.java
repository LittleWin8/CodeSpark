package top.littlewin.codespark.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送邮箱验证码请求
 */
@Data
public class EmailCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮箱
     */
    private String userEmail;

    /**
     * 场景：register / reset_password
     */
    private String scene;
}
