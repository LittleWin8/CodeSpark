package top.littlewin.codespark.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import top.littlewin.codespark.model.dto.user.UserQueryRequest;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.vo.LoginUserVO;
import top.littlewin.codespark.model.vo.UserVO;

import java.util.List;

/**
 * 用户表 服务层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userEmail     用户邮箱
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userEmail, String userPassword, String checkPassword);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return 脱敏后的登录用户信息
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @return 脱敏后的用户信息
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息(列表)
     *
     * @return 脱敏后的用户信息(列表)
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 用户登录（支持账号或邮箱）
     *
     * @param loginField   登录标识（账号或邮箱）
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String loginField, String userPassword, HttpServletRequest request);

    /**
     *
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     *
     * 用户注销登录
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
}
