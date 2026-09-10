package top.littlewin.codespark.service;

import com.mybatisflex.core.service.IService;
import top.littlewin.codespark.model.entity.UserTokenUsage;

/**
 * 用户 Token 消耗账本（按用户/模型/月份累计，永不重置） 服务层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
public interface UserTokenUsageService extends IService<UserTokenUsage> {

    /**
     * 记录用户单个模型月消耗总量
     *
     * @param userId 用户 ID
     * @param modelName 模型名称
     * @param input 输入 Token
     * @param output 输出 Toekn
     * @param total 总 Token
     */
    void recordUsage(Long userId, String modelName, long input, long output, long total);

    /**
     * 获取用户消耗的全部 Token
     * @param userId
     * @return 用户消耗的全部 Token
     */
    Long sumTokensByUserId(Long userId);

}
