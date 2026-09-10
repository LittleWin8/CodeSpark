package top.littlewin.codespark.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import top.littlewin.codespark.model.entity.UserQuotaUsage;
import top.littlewin.codespark.model.vo.AdminQuotaUsageVO;

import java.util.List;

public interface UserQuotaUsageMapper extends BaseMapper<UserQuotaUsage> {

    /**
     * 累加某月用户配额
     * @param userId 用户 ID
     * @param month 月份
     * @param tokens 消耗Tokens
     * @return 成功条数，1 成功， 0失败
     */
    int incrUsedTokens(@Param("userId") Long userId, @Param("month") Integer month,
                       @Param("tokens") long tokens);

    /**
     * 重置用户额度
     * @param month 月份
     * @return 重置成功的数量
     */
    int resetAllQuota(@Param("month") Integer month);
}