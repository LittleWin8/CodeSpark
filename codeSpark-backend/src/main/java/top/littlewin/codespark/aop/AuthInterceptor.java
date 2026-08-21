package top.littlewin.codespark.aop;

import jakarta.annotation.Resource;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.littlewin.codespark.annotation.AuthCheck;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.UserRoleEnum;
import top.littlewin.codespark.service.UserService;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;


    /**
     *
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     * @return
     * @throws Throwable
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable{

        // 1. 获取权限信息
        String mustRole  = authCheck.mustRole();

        // 2. 获取当前登录用户
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes)requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);

        // 3. 不需要权限，直接放行
        if (mustRole == null){
            return joinPoint.proceed();
        }

        // 4. 获取登录用户权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());

        // 5. 用户权限为空，直接拒绝
        if (userRoleEnum == null){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 6. 用户没有管理员权限，拒绝
        if (UserRoleEnum.ADMIN.equals(mustRole) && !UserRoleEnum.ADMIN.equals(loginUser.getUserRole())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 7. 全部校验通过，放行
        return joinPoint.proceed();
    }
}
