package top.littlewin.codespark.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件业务配置（连接参数如 host/port/账号密码由 spring.mail.* 交由 starter 装配，
 * 此处仅保留业务自有的开关与发件人）
 */
@Data
@Component
@ConfigurationProperties(prefix = "codespark.mail")
public class MailProperties {

    /**
     * 邮件服务总开关：关闭时不发送（接口仍按成功处理，避免联调受阻）
     */
    private boolean enabled = true;

    /**
     * 发件人显示地址（自有域名后改为 noreply@xxx.com）
     */
    private String from;
}
