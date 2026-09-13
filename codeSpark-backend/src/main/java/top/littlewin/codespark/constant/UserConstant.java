package top.littlewin.codespark.constant;

public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";
    
    // endregion

    /**
     * 管理员重置密码后的强制下线标记：user:pwdkick:{userId}（值为重置时间戳）
     */
    String USER_PWD_KICK_KEY = "user:pwdkick:";

    /**
     * 用户头像上传目录：tmp/user_avatar/{userId}
     */
    String USER_AVATAR_ROOT_DIR = System.getProperty("user.dir") + "/tmp/user_avatar";
}
