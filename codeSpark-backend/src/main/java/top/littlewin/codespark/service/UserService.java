package top.littlewin.codespark.service;

import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.vo.LoginUserVO;

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
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     *
     * 加密
     *
     * @param userPassword 用户密码
     * @return 加密后的用户密码
     */
    public String getEncryptPassword(String userPassword);
}
