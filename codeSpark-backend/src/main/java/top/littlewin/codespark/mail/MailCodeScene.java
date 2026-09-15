package top.littlewin.codespark.mail;

/**
 * 验证码用途场景：不同场景的验证码存不同 Redis key，不可跨场景使用
 */
public enum MailCodeScene {

    /**
     * 注册校验
     */
    REGISTER("register"),

    /**
     * 找回密码
     */
    RESET_PASSWORD("reset_password");

    private final String value;

    MailCodeScene(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 按前端传入的场景值解析，未知返回 null
     */
    public static MailCodeScene of(String value) {
        if (value == null) {
            return null;
        }
        for (MailCodeScene scene : values()) {
            if (scene.value.equals(value)) {
                return scene;
            }
        }
        return null;
    }
}
