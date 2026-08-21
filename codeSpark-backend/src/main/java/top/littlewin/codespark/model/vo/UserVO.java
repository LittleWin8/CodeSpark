package top.littlewin.codespark.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息封装类（脱敏）
 */
@Data
public class UserVO implements Serializable {

    /**
     * id
     */
    private Long id;
    
    /**
     * 账号
     */
    private String userAccount;

    /**
     * 邮箱
     */
    private String userEmail;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像（解析后可访问的 URL：oss: → 预签名 URL；local: → 本地静态地址）
     */
    private String userAvatar;

    /**
     * 用户头像存储标识（oss:... 或 local:...，供前端上传/编辑回显时提交入库，与 userAvatar 展示值分离）
     */
    private String userAvatarKey;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
