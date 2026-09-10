package top.littlewin.codespark.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import top.littlewin.codespark.model.entity.UserQuotaUsage;
import top.littlewin.codespark.model.vo.AdminQuotaUsageVO;
import top.littlewin.codespark.model.vo.QuotaInfoVO;

/**
 * 用户月额度计数器（仅计费模型消耗，管理员可重置） 服务层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
public interface UserQuotaUsageService extends IService<UserQuotaUsage> {

    /**
     * 记录用户配额消耗情况
     *
     * @param userId 用户 ID
     * @param tokens 消耗 Tokens
     */
    void recordQuota(Long userId, long tokens);

    /**
     * 获取个人配额情况
     *
     * @param userId 用户 ID
     * @return 配额信息
     */
    QuotaInfoVO getMyQuota(Long userId);

    /**
     * 检查是否超额
     *
     * @param userId 用户 ID
     */
    void checkQuota(Long userId);

    /**
     * 管理员重置指定用户额度
     *
     * @param userId 用户 ID
     */
    void adminReset(Long userId);

    /**
     * 管理员重置所有用户额度
     *
     */
    void adminResetAll();

    /**
     * 分页获取 Tokens 额度消耗列表
     * @param pageNum 页数
     * @param pageSize 每页大小
     * @return 额度列表
     */
    Page<AdminQuotaUsageVO> adminPageUsage(long pageNum, long pageSize);
}
