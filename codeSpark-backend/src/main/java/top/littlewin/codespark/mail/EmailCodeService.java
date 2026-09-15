package top.littlewin.codespark.mail;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 邮箱验证码服务：生成 / 发送 / 校验，含三道防刷闸与失败锁定。
 * <p>
 * Redis key 约定：
 * <ul>
 *   <li>email:code:{scene}:{email}  验证码本体，TTL 5 分钟</li>
 *   <li>email:cd:lock:{email}       重发间隔锁，60 秒</li>
 *   <li>email:cd:day:{email}:{date} 当日发码计数，10 条</li>
 *   <li>email:cd:fail:{email}       校验失败计数，5 次锁 15 分钟</li>
 * </ul>
 */
@Slf4j
@Service
public class EmailCodeService {

    /**
     * 验证码有效期（分钟）
     */
    private static final long CODE_TTL_MINUTES = 5;
    /**
     * 同邮箱重发最小间隔（秒）
     */
    private static final long RESEND_LOCK_SECONDS = 60;
    /**
     * 同邮箱每日发码上限
     */
    private static final long DAILY_LIMIT = 10;
    /**
     * 校验失败次数上限，超过则锁定
     */
    private static final long MAX_FAIL_COUNT = 5;
    /**
     * 失败锁定时长（分钟）
     */
    private static final long FAIL_LOCK_MINUTES = 15;

    /**
     * 验证码随机源：CSPRNG（RandomUtil 走 ThreadLocalRandom，非密码学安全）
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MailService mailService;

    /**
     * 发送验证码：限流 → 生成 → 存 Redis（按场景隔离）→ 异步发信
     *
     * @param email 收件邮箱
     * @param scene 验证码用途
     */
    public void sendCode(String email, MailCodeScene scene) {
        // 闸1：同邮箱 60s 内 1 条（setIfAbsent 原子占坑，TTL 即锁期）
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent("email:cd:lock:" + email, "1", Duration.ofSeconds(RESEND_LOCK_SECONDS));
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, ErrorMessage.MAIL_CODE_TOO_FREQUENT);
        }

        // 闸2：同邮箱每日上限（incr；首次写入时把 TTL 设到次日零点）
        String dayKey = "email:cd:day:" + email + ":" + LocalDate.now();
        Long dayCount = stringRedisTemplate.opsForValue().increment(dayKey);
        if (dayCount != null && dayCount == 1) {
            LocalDateTime tomorrow = LocalDate.now().plusDays(1).atStartOfDay();
            Duration ttl = Duration.between(LocalDateTime.now(), tomorrow);
            // 正好午夜时 ttl 可能为 0/负，此时至少给 1 秒，避免 expire 抛异常
            stringRedisTemplate.expire(dayKey, ttl.isNegative() || ttl.isZero()
                    ? Duration.ofSeconds(1) : ttl);
        }
        if (dayCount != null && dayCount > DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, ErrorMessage.MAIL_CODE_DAILY_LIMIT);
        }

        // 生成 6 位数字码（CSPRNG），scene 进 key：注册码不能当重置码用
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(
                "email:code:" + scene.getValue() + ":" + email,
                code, Duration.ofMinutes(CODE_TTL_MINUTES));

        // 异步发信，不阻塞请求线程
        mailService.sendCodeAsync(email, code, scene);
        log.info("验证码已入队发送: email={}, scene={}", email, scene);
    }

    /**
     * 校验验证码：失败计数锁定 → 比对 → 成功即删（一次有效，防重放）
     *
     * @param email 收件邮箱
     * @param scene 验证码用途（必须与发送时一致）
     * @param code  用户提交的验证码
     */
    public void verify(String email, MailCodeScene scene, String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.MAIL_CODE_REQUIRED);
        }

        // 失败锁定：超过次数直接拒绝，防暴力猜 6 位码
        String failKey = "email:cd:fail:" + email;
        String failStr = stringRedisTemplate.opsForValue().get(failKey);
        if (failStr != null && Long.parseLong(failStr) >= MAX_FAIL_COUNT) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, ErrorMessage.MAIL_CODE_LOCKED);
        }

        String real = stringRedisTemplate.opsForValue()
                .get("email:code:" + scene.getValue() + ":" + email);
        if (real == null || !real.equals(code)) {
            Long fails = stringRedisTemplate.opsForValue().increment(failKey);
            if (fails != null && fails == 1) {
                stringRedisTemplate.expire(failKey, Duration.ofMinutes(FAIL_LOCK_MINUTES));
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.MAIL_CODE_INVALID);
        }

        // 成功：删码防重放 + 清失败计数
        stringRedisTemplate.delete("email:code:" + scene.getValue() + ":" + email);
        stringRedisTemplate.delete(failKey);
    }
}
