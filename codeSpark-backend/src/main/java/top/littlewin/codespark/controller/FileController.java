package top.littlewin.codespark.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
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
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.constant.UserConstant;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.service.AppService;
import top.littlewin.codespark.service.FileStorageService;
import top.littlewin.codespark.service.UserService;

/**
 * 文件上传与访问
 * <p>
 * - 头像：tmp/user_avatar/{userId}/xxx
 * - 封面：tmp/app_cover/{appId}/xxx
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @jakarta.annotation.Resource
    private UserService userService;

    @jakarta.annotation.Resource
    private AppService appService;

    @jakarta.annotation.Resource
    private FileStorageService fileStorageService;

    /**
     * 上传用户头像，返回可访问的 URL
     */
    @PostMapping("/upload/avatar")
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String url = fileStorageService.saveFile(file, UserConstant.USER_AVATAR_ROOT_DIR, loginUser.getId(), "avatar");
        return ResultUtils.success(url);
    }

    /**
     * 上传应用封面，返回可访问的 URL
     *
     * @param file  上传的文件
     * @param appId 应用 ID（封面归属的应用，保存到 tmp/app_cover/{appId}）
     */
    @PostMapping("/upload/cover")
    public BaseResponse<String> uploadCover(@RequestParam("file") MultipartFile file,
                                            @RequestParam("appId") Long appId,
                                            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        // 校验应用存在，且仅本人或管理员可上传封面
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "APP_NOT_FOUND");
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        String url = fileStorageService.saveFile(file, AppConstant.APP_COVER_ROOT_DIR, appId, "cover");
        return ResultUtils.success(url);
    }

    /**
     * 静态访问：/file/avatar/{userId}/{fileName}
     */
    @GetMapping("/avatar/{userId}/{fileName}")
    public ResponseEntity<Resource> getAvatar(@PathVariable Long userId,
                                              @PathVariable String fileName) {
        Resource resource = fileStorageService.loadFile(UserConstant.USER_AVATAR_ROOT_DIR, userId, fileName);
        return buildFileResponse(resource);
    }

    /**
     * 静态访问：/file/cover/{appId}/{fileName}
     */
    @GetMapping("/cover/{appId}/{fileName}")
    public ResponseEntity<Resource> getCover(@PathVariable Long appId,
                                             @PathVariable String fileName) {
        Resource resource = fileStorageService.loadFile(AppConstant.APP_COVER_ROOT_DIR, appId, fileName);
        return buildFileResponse(resource);
    }

    /**
     * 将文件资源包装为响应；资源不可访问时返回 404
     */
    private ResponseEntity<Resource> buildFileResponse(Resource resource) {
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
