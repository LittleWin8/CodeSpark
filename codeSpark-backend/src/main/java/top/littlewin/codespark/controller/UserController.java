package top.littlewin.codespark.controller;

import jakarta.annotation.Resource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import top.littlewin.codespark.annotation.AuthCheck;
import top.littlewin.codespark.common.BaseResponse;
import top.littlewin.codespark.common.DeleteRequest;
import top.littlewin.codespark.common.ResultUtils;
import top.littlewin.codespark.constant.UserConstant;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.model.dto.user.*;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.vo.LoginUserVO;
import top.littlewin.codespark.model.vo.UserVO;
import top.littlewin.codespark.service.UserService;
import top.littlewin.codespark.utils.PasswordUtils;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户表 控制层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private PasswordUtils passwordUtils;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userEmail = userRegisterRequest.getUserEmail();
        String emailCode = userRegisterRequest.getEmailCode();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userEmail, emailCode, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     *
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求
     * @param request 请求对象
     * @return 脱敏后的登录用户信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     *
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     *
     * 用户注销
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户（仅管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 使用配置文件中的默认密码，BCrypt 加密
        user.setUserPassword(passwordUtils.encryptDefault());
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类（仅本人或管理员）。
     * 修复：原来内部自调用 getUserById(id)，AOP 代理不生效，
     * @AuthCheck 被绕过，未登录可按 id 枚举全站账号/邮箱。现改为方法内独立鉴权，
     * 不再复用管理员接口。
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 未登录直接抛 NOT_LOGIN_ERROR
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        // 非管理员只能查自己
        ThrowUtils.throwIf(!isAdmin && (loginUser.getId() == null || id != loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户（仅管理员，且不可以删除自己）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest,
                                            HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前登录管理员
        User loginUser = userService.getLoginUser(request);
        // 不可以删除自己
        if (deleteRequest.getId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, ErrorMessage.CANNOT_DELETE_SELF);
        }
        // 删除目标必须存在
        User targetUser = userService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(targetUser == null, ErrorCode.NOT_FOUND_ERROR);
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }


    /**
     * 用户修改自己的密码（成功后所有会话失效，需用新密码重新登录）
     */
    @PostMapping("/updatePassword")
    public BaseResponse<Boolean> updateUserPassword(
            @RequestBody UserUpdatePasswordRequest updatePasswordRequest,
            HttpServletRequest request) {

        if (updatePasswordRequest == null
                || StrUtil.hasBlank(updatePasswordRequest.getOldPassword(),
                                    updatePasswordRequest.getNewPassword(),
                                    updatePasswordRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.EMPTY_PARAMS);
        }

        // 1. 登录校验 + 基础规则
        User loginUser = userService.getLoginUser(request);
        if (updatePasswordRequest.getNewPassword().length() < 8
                || updatePasswordRequest.getCheckPassword().length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.PASSWORD_TOO_SHORT);
        }
        if (!updatePasswordRequest.getNewPassword().equals(updatePasswordRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.PASSWORD_MISMATCH);
        }

        // 2. 当前密码必须正确
        User dbUser = userService.getById(loginUser.getId());
        ThrowUtils.throwIf(dbUser == null, ErrorCode.NOT_FOUND_ERROR);
        if (!passwordUtils.matches(updatePasswordRequest.getOldPassword(), dbUser.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.OLD_PASSWORD_ERROR);
        }
        if (updatePasswordRequest.getNewPassword().equals(updatePasswordRequest.getOldPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, ErrorMessage.PASSWORD_SAME);
        }

        // 3. 更新密码
        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setUserPassword(passwordUtils.encrypt(updatePasswordRequest.getNewPassword()));
        boolean result = userService.updateById(updateUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 4. 所有会话失效（复用踢人标记），用户需用新密码重新登录
        stringRedisTemplate.opsForValue().set(
                UserConstant.USER_PWD_KICK_KEY + loginUser.getId(),
                String.valueOf(System.currentTimeMillis()),
                java.time.Duration.ofDays(30));
        log.info("用户修改了密码并强制重新登录: user={}", loginUser.getUserAccount());
        return ResultUtils.success(true);
    }

    /**
     * 管理员重置用户密码为系统默认密码（不可重置自己，重置后强制该用户下线）
     */
    @PostMapping("/resetPassword")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> resetPassword(@RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest request) {

        // 1. 参数校验
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 获取当前登录管理员，不可重置自己
        User loginUser = userService.getLoginUser(request);
        if (deleteRequest.getId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, ErrorMessage.CANNOT_RESET_SELF);
        }

        // 3. 目标用户必须存在
        User targetUser = userService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(targetUser == null, ErrorCode.NOT_FOUND_ERROR);

        // 4. 重置为配置文件的默认密码
        User updateUser = new User();
        updateUser.setId(targetUser.getId());
        updateUser.setUserPassword(passwordUtils.encryptDefault());
        boolean result = userService.updateById(updateUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 5. 强制下线：写入踢人标记（时间戳），getLoginUser 会把创建时间早于该值的会话判为失效
        stringRedisTemplate.opsForValue().set(
                UserConstant.USER_PWD_KICK_KEY + targetUser.getId(),
                String.valueOf(System.currentTimeMillis()),
                java.time.Duration.ofDays(30));
        log.info("管理员重置了用户密码并强制下线: operator={}, target={}",
                loginUser.getUserAccount(), targetUser.getUserAccount());
        return ResultUtils.success(true);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新个人信息（本人，仅昵称/简介，不能改角色等敏感字段）
     * 头像信息由 FileController 中接口管理
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
                                              HttpServletRequest request) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 强制使用当前登录用户 id，防止越权修改他人信息
        User user = new User();
        user.setId(loginUser.getId());
        user.setUserName(userUpdateMyRequest.getUserName());
        user.setUserProfile(userUpdateMyRequest.getUserProfile());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        // 数据脱敏
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

}
