package top.littlewin.codespark.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import top.littlewin.codespark.model.dto.app.AppQueryRequest;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.model.vo.AppVO;

import java.util.List;

/**
 * 应用表 服务层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用封装类
     *
     * @param app
     * @return
     */
    public AppVO getAppVO(App app);

    /**
     * 获取应用封装列表
     *
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest
     * @return
     */
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);


}
