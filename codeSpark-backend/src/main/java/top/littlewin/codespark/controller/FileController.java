package top.littlewin.codespark.controller;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.littlewin.codespark.common.BaseResponse;
import top.littlewin.codespark.common.ResultUtils;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.FileBizEnum;
import top.littlewin.codespark.service.FileService;
import top.littlewin.codespark.service.UserService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文件上传与访问
 */
@RestController
@Slf4j
@RequestMapping("/file")
public class FileController {

    @Resource
    private UserService userService;

    @Resource
    private FileService fileService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 上传用户头像（上传即生效：保存到存储后立即更新用户头像字段，杜绝「未保存却残留 OSS 对象」）
     *
     * @return 存储标识（oss:... 或 local:...）
     */
    @PostMapping("/upload/avatar")
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) {

        // 1. 获取登录用户
        User loginUser = userService.getLoginUser(request);

        // 2. 限流：每天最多5次（Redis 日计数，自然日清零，失败不计次）
        String redisKey = "avatar:update:" + loginUser.getId() + ":" + LocalDate.now();
        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                LocalDateTime tomorrowMidnight = LocalDate.now().plusDays(1).atStartOfDay();
                Duration ttl = Duration.between(LocalDateTime.now(), tomorrowMidnight);
                if (!ttl.isNegative() && !ttl.isZero()) {
                    stringRedisTemplate.expire(redisKey, ttl);
                }
            }
            if (count != null && count > 5) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, ErrorMessage.AVATAR_UPDATE_LIMIT_EXCEEDED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("头像限流 Redis 异常，降级放行: userId={}", loginUser.getId(), e);
        }

        // 3. 保存头像
        String storageKey = fileService.saveAvatar(loginUser.getId(), file);
        if (StrUtil.isBlank(storageKey)) {
            try { stringRedisTemplate.opsForValue().decrement(redisKey); } catch (Exception ignore) {}
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, ErrorMessage.FILE_SAVE_FAILED);
        }

        // 4. 更新数据库
        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setUserAvatar(storageKey);
        boolean updated = userService.updateById(updateUser);
        if (!updated) {
            try { stringRedisTemplate.opsForValue().decrement(redisKey); } catch (Exception ignore) {}
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, ErrorMessage.FILE_SAVE_FAILED);
        }
        log.info("头像已更新: userId={}, key={}", loginUser.getId(), storageKey);
        return ResultUtils.success(storageKey);
    }

    /**
     * 静态访问本地文件：/file/{biz}/{yyyy}/{MM}/{dd}/{fileName}
     * （biz 区分 cover / avatar；OSS 对象通过签名 URL 直接访问，不走此端点）
     */
    @GetMapping("/{biz}/{yyyy}/{MM}/{dd}/{fileName}")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(@PathVariable("biz") String biz,
                                            @PathVariable("yyyy") String yyyy,
                                            @PathVariable("MM") String MM,
                                            @PathVariable("dd") String dd,
                                            @PathVariable("fileName") String fileName) {
        FileBizEnum bizEnum = FileBizEnum.getEnumByValue(biz);
        if (bizEnum == null) {
            return ResponseEntity.notFound().build();
        }
        String relativeKey = yyyy + "/" + MM + "/" + dd + "/" + fileName;
        org.springframework.core.io.Resource resource = fileService.loadFile(bizEnum, relativeKey);
        return buildFileResponse(resource);
    }

    /**
     * 将存储标识解析为可访问 URL（前端上传后预览 / 预签名 URL 过期兜底用）：
     * - oss: → 现签预签名 URL；local: → 本地静态访问地址；未知格式 → null
     */
    @GetMapping("/resolve")
    public BaseResponse<String> resolve(@RequestParam("storageKey") String storageKey) {
        ThrowUtils.throwIf(StrUtil.isBlank(storageKey), ErrorCode.PARAMS_ERROR, ErrorMessage.INVALID_STORAGE_REQUEST);
        return ResultUtils.success(fileService.resolveUrl(storageKey));
    }

    /**
     * 将文件资源包装为响应；资源不可访问时返回 404
     */
    private ResponseEntity<org.springframework.core.io.Resource> buildFileResponse(org.springframework.core.io.Resource resource) {
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
