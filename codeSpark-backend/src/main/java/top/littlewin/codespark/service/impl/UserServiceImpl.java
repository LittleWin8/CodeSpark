package top.littlewin.codespark.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.model.dto.user.UserQueryRequest;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.mapper.UserMapper;
import top.littlewin.codespark.model.enums.UserRoleEnum;
import top.littlewin.codespark.model.vo.LoginUserVO;
import top.littlewin.codespark.model.vo.UserVO;
import top.littlewin.codespark.service.UserService;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.utils.PasswordUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static top.littlewin.codespark.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户表 服务层实现。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

    /**
     * 邮箱格式正则
     */
    private static final Pattern EMAIL_REGEX =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    @Resource
    private PasswordUtils passwordUtils;

    @Override
    public long userRegister(String userAccount, String userEmail, String userPassword, String checkPassword) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userEmail, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "EMPTY_PARAMS");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ACCOUNT_TOO_SHORT");
        }
        // 邮箱必填 + 格式校验
        if (!isValidEmail(userEmail)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "INVALID_EMAIL");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "PASSWORD_TOO_SHORT");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "PASSWORD_MISMATCH");
        }

        // 2. 检查账号是否重复（必须用实体属性引用，PG 驼峰列名才会正确加引号）
        QueryWrapper accountWrapper = QueryWrapper.create()
                .where(User::getUserAccount).eq(userAccount);
        long accountCount = this.mapper.selectCountByQuery(accountWrapper);
        if (accountCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ACCOUNT_EXISTS");
        }
        // 3. 检查邮箱是否重复
        QueryWrapper emailWrapper = QueryWrapper.create()
                .where(User::getUserEmail).eq(userEmail);
        long emailCount = this.mapper.selectCountByQuery(emailWrapper);
        if (emailCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "EMAIL_EXISTS");
        }

        // 4. 生成唯一邀请码（短：数字 + 英文字母）
        String shareCode = generateUniqueShareCode();

        // 5. 加密
        String encryptPassword = passwordUtils.encrypt(userPassword);

        // 6. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserEmail(userEmail);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        user.setShareCode(shareCode);
        // 默认非会员：会员相关字段显式置空（vipExpireTime/vipCode/vipNumber 均为空）
        user.setVipExpireTime(null);
        user.setVipCode(null);
        user.setVipNumber(null);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "REGISTER_FAILED");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null){
            return null;
        }

        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user,userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)){
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public LoginUserVO userLogin(String loginField, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(loginField, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "EMPTY_ACCOUNT_OR_PASSWORD");
        }
        if (loginField.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "INVALID_ACCOUNT");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "INVALID_PASSWORD");
        }
        // 2. 查询用户是否存在，支持账号或邮箱登录（必须用实体属性引用，PG 驼峰列名才会正确加引号）
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(User::getUserAccount).eq(loginField)
                .or(User::getUserEmail).eq(loginField);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "LOGIN_FAILED");
        }
        // 3. 验证密码
        if (!passwordUtils.matches(userPassword, user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "LOGIN_FAILED");
        }

        // 4. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);

        // 5. 获得脱敏后的用户信息
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 1. 判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 2. 从数据库获取最新的用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 1. 判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "NOT_LOGIN");
        }
        // 2. 移除登录状态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userEmail = userQueryRequest.getUserEmail();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(User::getId).eq(id)
                .and(User::getUserRole).eq(userRole)
                .and(User::getUserAccount).like(userAccount)
                .and(User::getUserEmail).like(userEmail)
                .and(User::getUserName).like(userName)
                .and(User::getUserProfile).like(userProfile);
        if (sortField != null && !sortField.isEmpty()) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        return queryWrapper;
    }

    /**
     * 校验邮箱格式
     */
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_REGEX.matcher(email).matches();
    }

    /**
     * 生成唯一邀请码：6 位数字 + 英文字母，查库保证不重复
     */
    private String generateUniqueShareCode() {
        for (int i = 0; i < 10; i++) {
            String code = RandomUtil.randomString(6);
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .where(User::getShareCode).eq(code);
            long count = this.mapper.selectCountByQuery(queryWrapper);
            if (count == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "REGISTER_FAILED");
    }
}
