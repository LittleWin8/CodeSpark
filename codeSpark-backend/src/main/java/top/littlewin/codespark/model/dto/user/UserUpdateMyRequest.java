package top.littlewin.codespark.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户本人更新请求（仅包含本人可编辑字段，防止越权修改角色等敏感字段）
 */
@Data
public class UserUpdateMyRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    private static final long serialVersionUID = 1L;
}
