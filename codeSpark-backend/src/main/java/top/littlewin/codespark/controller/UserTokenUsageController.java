package top.littlewin.codespark.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import top.littlewin.codespark.common.BaseResponse;
import top.littlewin.codespark.common.ResultUtils;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.service.UserService;
import top.littlewin.codespark.service.UserTokenUsageService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 Token 消耗账本（按用户/模型/月份累计，永不重置） 控制层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@RestController
@RequestMapping("/userTokenUsage")
public class UserTokenUsageController {

    @Resource
    private UserTokenUsageService userTokenUsageService;

    @Resource
    private UserService userService;

    /**
     * 获取当前登陆用户消耗的总 Token 数量
     * @param request 请求体
     * @return 用户消耗的总 Token 数量
     */
    @GetMapping("/me/totalTokens")
    public BaseResponse<Long> getTotalTokens(HttpServletRequest request){

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        return ResultUtils.success(userTokenUsageService.sumTokensByUserId(loginUser.getId()));
    }

}
