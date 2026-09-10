package top.littlewin.codespark.controller;

import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import top.littlewin.codespark.annotation.AuthCheck;
import top.littlewin.codespark.common.BaseResponse;
import top.littlewin.codespark.common.ResultUtils;
import top.littlewin.codespark.constant.UserConstant;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.dto.quota.QuotaUsageQueryRequest;
import top.littlewin.codespark.model.dto.quota.ResetQuotaRequest;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.vo.AdminQuotaUsageVO;
import top.littlewin.codespark.model.vo.QuotaInfoVO;
import top.littlewin.codespark.service.UserQuotaUsageService;
import top.littlewin.codespark.service.UserService;

/**
 * 用户月额度计数器（仅计费模型消耗，管理员可重置） 控制层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@RestController
@RequestMapping("/quota")
public class UserQuotaUsageController {

    @Resource
    private UserQuotaUsageService userQuotaUsageService;

    @Resource
    private UserService userService;

    /**
     * 当前用户额度信息（前端额度环）
     */
    @GetMapping("/me")
    public BaseResponse<QuotaInfoVO> getMyQuota(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        return ResultUtils.success(userQuotaUsageService.getMyQuota(loginUser.getId()));
    }

    /**
     * 管理看板分页（仅管理员）
     */
    @PostMapping("/admin/usage/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminQuotaUsageVO>> adminPageUsage(
            @RequestBody QuotaUsageQueryRequest quotaUsageQueryRequest) {
        ThrowUtils.throwIf(quotaUsageQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = quotaUsageQueryRequest.getPageNum();
        long pageSize = quotaUsageQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageNum <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userQuotaUsageService.adminPageUsage(pageNum, pageSize));
    }

    /**
     * 管理员重置指定用户当月额度（仅管理员）
     */
    @PostMapping("/admin/reset")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adminReset(@RequestBody ResetQuotaRequest resetRequest) {
        ThrowUtils.throwIf(resetRequest == null || resetRequest.getUserId() == null
                        || resetRequest.getUserId() <= 0, ErrorCode.PARAMS_ERROR);
        userQuotaUsageService.adminReset(resetRequest.getUserId());
        return ResultUtils.success(true);
    }

    /**
     * 管理员一键重置所有用户当月额度（仅管理员）
     */
    @PostMapping("/admin/reset/all")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adminResetAll() {
        userQuotaUsageService.adminResetAll();
        return ResultUtils.success(true);
    }

}
