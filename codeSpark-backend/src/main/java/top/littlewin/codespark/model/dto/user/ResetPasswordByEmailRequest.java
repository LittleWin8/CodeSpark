package top.littlewin.codespark.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 通过邮箱验证码重置密码请求
 */
@Data
public class ResetPasswordByEmailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮箱
     */
    private String userEmail;

    /**
     * 邮箱验证码
     */
    private String emailCode;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;
}
