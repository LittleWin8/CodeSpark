package top.littlewin.codespark.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.model.dto.app.AppQueryRequest;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.mapper.AppMapper;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.vo.AppVO;
import top.littlewin.codespark.model.vo.UserVO;
import top.littlewin.codespark.service.AppService;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用表 服务层实现。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    @Resource
    private UserService userService;

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }


    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        // 必须用实体属性引用，PG 驼峰列名才会正确加引号
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(App::getId).eq(id)
                .and(App::getAppName).like(appName)
                .and(App::getCover).like(cover)
                .and(App::getInitPrompt).like(initPrompt)
                .and(App::getCodeGenType).eq(codeGenType)
                .and(App::getDeployKey).eq(deployKey)
                .and(App::getPriority).eq(priority)
                .and(App::getUserId).eq(userId);
        // sortField 为运行时变量，无法用 Lambda 引用，需自行保证与 PG 实际列名匹配
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        return queryWrapper;
    }


}
