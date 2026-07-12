package top.littlewin.codespark.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码加密工具
 * 使用 BCrypt 算法，盐值自动生成并嵌入密文
 */
@Component
public class PasswordUtils {

    @Value("${codespark.password.strength:10}")
    private int strength;

    @Value("${codespark.password.default:12345678}")
    private String defaultPassword;

    private BCryptPasswordEncoder encoder;

    @PostConstruct
    public void init() {
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    /**
     * BCrypt 加密
     *
     * @param rawPassword 原始密码
     * @return 加密后的密文 (格式: $2a$10$...)
     */
    public String encrypt(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * 获取默认密码（从配置文件注入）
     */
    public String getDefaultPassword() {
        return defaultPassword;
    }

    /**
     * 返回默认密码的 BCrypt 密文
     */
    public String encryptDefault() {
        return encrypt(defaultPassword);
    }

    /**
     * 校验密码是否匹配
     *
     * @param rawPassword       原始密码
     * @param encryptedPassword 密文
     * @return 是否匹配
     */
    public boolean matches(String rawPassword, String encryptedPassword) {
        return encoder.matches(rawPassword, encryptedPassword);
    }
}
