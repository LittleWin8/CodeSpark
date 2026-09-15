package top.littlewin.codespark.mail;

import jakarta.annotation.Resource;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务：封装 JavaMailSender，验证码邮件异步发送。
 * 发送失败只记日志不回滚：验证码已入 Redis，用户可重发。
 */
@Slf4j
@Service
public class MailService {

    @Resource
    private JavaMailSender mailSender;

    @Resource
    private MailProperties mailProperties;

    /**
     * 异步发送验证码邮件：丢虚拟线程，避免 1~3s 的 SMTP 往返阻塞请求线程
     */
    public void sendCodeAsync(String to, String code, MailCodeScene scene) {
        Thread.ofVirtual().name("mail-code-" + to).start(() -> sendCode(to, code, scene));
    }

    private void sendCode(String to, String code, MailCodeScene scene) {
        if (!mailProperties.isEnabled()) {
            log.warn("邮件服务未启用，跳过发送: to={}, scene={}", to, scene);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailProperties.getFrom(), "CodeSpark", "UTF-8"));
            helper.setTo(to);
            helper.setSubject("CodeSpark 验证码：" + code);
            helper.setText("""
                    <div style="max-width:480px;margin:0 auto;font-family:Arial,sans-serif;color:#333">
                      <h2>邮箱验证码</h2>
                      <p>你的验证码（5 分钟内有效）：</p>
                      <p style="font-size:28px;font-weight:bold;letter-spacing:6px;color:#1677ff">%s</p>
                      <p style="color:#999;font-size:12px">若非本人操作，请忽略本邮件。</p>
                    </div>
                    """.formatted(code), true);
            mailSender.send(message);
            log.info("验证码邮件已发送: to={}, scene={}", to, scene);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: to={}, scene={}", to, scene, e);
        }
    }
}
