package top.littlewin.codespark.controller;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.littlewin.codespark.annotation.RateLimit;
import top.littlewin.codespark.common.BaseResponse;
import top.littlewin.codespark.common.ResultUtils;
import top.littlewin.codespark.constant.UserConstant;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.mail.EmailCodeService;
import top.littlewin.codespark.mail.MailCodeScene;
import top.littlewin.codespark.model.dto.user.EmailCodeRequest;
import top.littlewin.codespark.model.dto.user.ResetPasswordByEmailRequest;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.RateLimitType;
import top.littlewin.codespark.service.UserService;
import top.littlewin.codespark.utils.PasswordUtils;

/**
 * 邮箱验证码 控制层：发送验证码、邮箱重置密码。
 * 具体发送与校验逻辑在 mail 包（EmailCodeService / MailService）。
 */
@Slf4j
@RestController
@RequestMapping("/email")
public class EmailController {

    @Resource
    private UserService userService;

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private PasswordUtils passwordUtils;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送邮箱验证码。
     * 匿名可用（注册场景用户尚未存在），滥用由三道防刷闸 + 接口限流共同约束。
     * 找回密码场景不暴露邮箱是否注册（未注册也返回成功，仅不发送）。
     */
    @PostMapping("/code")
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "请求过于频繁，请稍后再试")
    public BaseResponse<Boolean> sendEmailCode(@RequestBody EmailCodeRequest emailCodeRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(emailCodeRequest == null, ErrorCode.PARAMS_ERROR);
        String email = StrUtil.trim(emailCodeRequest.getUserEmail());
        ThrowUtils.throwIf(StrUtil.isBlank(email) || !Validator.isEmail(email),
                ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_EMAIL);
        MailCodeScene scene = MailCodeScene.of(emailCodeRequest.getScene());
        ThrowUtils.throwIf(scene == null, ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_MAIL_SCENE);

        boolean exists = userService.getByEmail(email) != null;
        if (scene == MailCodeScene.REGISTER) {
            // 注册场景：邮箱已注册直接提示，体验优先
            ThrowUtils.throwIf(exists, ErrorCode.PARAMS_ERROR, ErrorMessage.EMAIL_EXISTS);
            emailCodeService.sendCode(email, scene);
        } else {
            // 找回密码：未注册也返回成功（防账号枚举），但不实际发送
            if (exists) {
                emailCodeService.sendCode(email, scene);
            } else {
                log.info("找回密码请求的邮箱未注册，静默忽略: {}", email);
            }
        }
        return ResultUtils.success(true);
    }

    /**
     * 通过邮箱验证码重置密码（成功后该用户全部会话失效，需重新登录）
     */
    @PostMapping("/resetPassword")
    public BaseResponse<Boolean> resetPasswordByEmail(@RequestBody ResetPasswordByEmailRequest request,
                                                      HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null
                        || StrUtil.hasBlank(request.getUserEmail(), request.getEmailCode(),
                                            request.getNewPassword(), request.getCheckPassword()),
                ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_PARAMS);

        // 1. 校验验证码（失败锁定由 EmailCodeService 负责）
        emailCodeService.verify(request.getUserEmail().trim(),
                MailCodeScene.RESET_PASSWORD, request.getEmailCode());

        // 2. 定位用户
        User user = userService.getByEmail(request.getUserEmail().trim());
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, ErrorMessage.EMAIL_NOT_FOUND);

        // 3. 新密码规则校验
        if (request.getNewPassword().length() < 8 || request.getCheckPassword().length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.PASSWORD_TOO_SHORT);
        }
        if (!request.getNewPassword().equals(request.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.PASSWORD_MISMATCH);
        }

        // 4. 更新密码
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserPassword(passwordUtils.encrypt(request.getNewPassword()));
        boolean updated = userService.updateById(updateUser);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);

        // 5. 强制下线：写入踢人标记，旧会话创建时间早于该值即失效
        stringRedisTemplate.opsForValue().set(
                UserConstant.USER_PWD_KICK_KEY + user.getId(),
                String.valueOf(System.currentTimeMillis()),
                java.time.Duration.ofDays(30));
        log.info("用户通过邮箱验证码重置密码: email={}", request.getUserEmail());
        return ResultUtils.success(true);
    }
}
