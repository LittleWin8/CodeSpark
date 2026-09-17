package top.littlewin.codespark.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import top.littlewin.codespark.model.entity.UserTokenUsage;
import top.littlewin.codespark.model.vo.AdminQuotaUsageVO;
import top.littlewin.codespark.model.vo.UserTokenSumVO;

import java.util.List;

/**
 * 用户 Token 消耗账本（按用户/模型/月份累计，永不重置） 映射层。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
public interface UserTokenUsageMapper extends BaseMapper<UserTokenUsage> {

    /**
     * 累加用户消耗 Tokens
     *
     * @param userId 用户 ID
     * @param modelName 模型名词
     * @param month 月份
     * @param input 输入 Tokens
     * @param output 输出 Tokens
     * @param total 总消耗 Tokens
     * @return 更新条数，1 更新成功；0 更新失败
     */
    int incrTokens(@Param("userId") Long userId, @Param("modelName") String modelName, @Param("month") Integer month,
                   @Param("input") long input, @Param("output") long output, @Param("total") long total);

    /**
     * 获取个人消耗总 Tokens
     *
     * @param userId 用户 ID
     * @return
     */
    long sumTokensByUserId(@Param("userId") Long userId);

    /**
     * 获取用户消耗 Tokens 列表
     * @param userIds 用户列表
     * @return 用户消耗总量列表
     */
    List<UserTokenSumVO> sumTokensByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 平台当月真实 Token 消耗总额（账本聚合）
     */
    Long selectPlatformUsedTokens(@Param("month") Integer month);

    /**
     * 用户消耗排行分页：按历史总消耗排序
     */
    List<AdminQuotaUsageVO> selectRankedUsersByTotal(@Param("asc") boolean asc,
                                                     @Param("limit") long limit,
                                                     @Param("offset") long offset);

    /**
     * 按用户批量取账本当月消耗
     */
    List<UserTokenSumVO> selectMonthTokensByUserIds(@Param("month") Integer month,
                                                    @Param("userIds") List<Long> userIds);

    /**
     * 用户消耗排行分页：按本月已用排序
     */
    List<AdminQuotaUsageVO> selectRankedUsersByUsed(@Param("month") Integer month,
                                                    @Param("asc") boolean asc,
                                                    @Param("limit") long limit,
                                                    @Param("offset") long offset);

    /**
     * 参与排行的用户总数
     */
    Long countRankedUsers();
}
