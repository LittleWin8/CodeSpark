package top.littlewin.codespark.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.model.dto.app.AppQueryRequest;
import top.littlewin.codespark.model.entity.App;
import top.littlewin.codespark.model.entity.User;
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
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 对话生成应用代码
     *
     * @param appId 应用 ID
     * @param message 用户提示词
     * @param loginUser 登录用户
     * @return 流式响应结果
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 应用部署
     *
     * @param appId 应用 ID
     * @param loginUser 登录用户
     * @return 可访问的部署地址
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 删除应用及磁盘文件
     */
    boolean deleteAppAndFiles(Long appId);
}
