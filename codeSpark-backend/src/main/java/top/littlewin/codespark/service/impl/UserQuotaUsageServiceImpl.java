package top.littlewin.codespark.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import top.littlewin.codespark.config.QuotaProperties;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.mapper.UserTokenUsageMapper;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.entity.UserQuotaUsage;
import top.littlewin.codespark.mapper.UserQuotaUsageMapper;
import top.littlewin.codespark.model.vo.AdminQuotaUsageVO;
import top.littlewin.codespark.model.vo.QuotaInfoVO;
import top.littlewin.codespark.model.vo.UserTokenSumVO;
import top.littlewin.codespark.service.UserQuotaUsageService;
import org.springframework.stereotype.Service;
import top.littlewin.codespark.service.UserService;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户月额度计数器（仅计费模型消耗，管理员可重置） 服务层实现。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Slf4j
@Service
public class UserQuotaUsageServiceImpl extends ServiceImpl<UserQuotaUsageMapper, UserQuotaUsage>  implements UserQuotaUsageService{

    @Resource
    private UserQuotaUsageMapper userQuotaUsageMapper;

    @Resource
    private UserTokenUsageMapper userTokenUsageMapper;

    @Resource
    private UserService userService;

    @Resource
    private QuotaProperties quotaProperties;

    @Override
    public void recordQuota(Long userId, long tokens) {

        // 1. 参数校验
        if (userId == null || tokens <= 0){
            return;
        }

        // 2. 获取年月
        int month = currentMonth();

        // 3. 更新数据库（若没有数据即为更新失败，则插入数据)
        try {

            int updated = userQuotaUsageMapper.incrUsedTokens(userId, month, tokens);

            if (updated == 0){
                insertRow(userId, month, tokens);
            }

        }catch (DuplicateKeyException e){
            userQuotaUsageMapper.incrUsedTokens(userId, month, tokens);
        } catch (Exception e){
            log.error("Token 月额度记录失败： userId={}, month={}, error={}", userId, month, e.getMessage());
        }
    }

    @Override
    public QuotaInfoVO getMyQuota(Long userId) {

        long used = getUsedTokens(userId);
        QuotaInfoVO info = new QuotaInfoVO();
        info.setEnabled(quotaProperties.isEnabled());
        info.setUnlimited(isUnlimited());
        info.setMonthlyLimit(quotaProperties.getMonthlyTokens());
        info.setUsedTokens(used);
        info.setRemainingTokens(isUnlimited() ? -1L
                : Math.max(0, quotaProperties.getMonthlyTokens() - used));
        return info;
    }

    @Override
    public void checkQuota(Long userId) {
        if (isUnlimited()) {
            return;
        }
        ThrowUtils.throwIf(getUsedTokens(userId) >= quotaProperties.getMonthlyTokens(), ErrorCode.OPERATION_ERROR, ErrorMessage.QUOTA_EXCEEDED);
    }

    @Override
    public void adminReset(Long userId) {
        UpdateChain.of(UserQuotaUsage.class)
                .set(UserQuotaUsage::getUsedTokens, 0L)
                .set(UserQuotaUsage::getResetTime, LocalDateTime.now())
                .where(UserQuotaUsage::getUserId).eq(userId)
                .and(UserQuotaUsage::getMonth).eq(currentMonth())
                .update();
    }

    @Override
    public void adminResetAll() {
        userQuotaUsageMapper.resetAllQuota(currentMonth());
    }

    @Override
    public Page<AdminQuotaUsageVO> adminPageUsage(long pageNum, long pageSize) {

        // 1. 分页查询当月额度消耗（按消耗倒序）
        int month = currentMonth();
        Page<UserQuotaUsage> usagePage = this.page(Page.of(pageNum, pageSize),
                QueryWrapper.create()
                        .where(UserQuotaUsage::getMonth).eq(month)
                        .orderBy(UserQuotaUsage::getUsedTokens, false));

        // 2. 收集页内用户 ID，用于批量补充关联信息（避免 N+1）
        List<Long> userIds = usagePage.getRecords().stream()
                .map(UserQuotaUsage::getUserId).toList();

        // 3. 批量查用户账号：userId -> userAccount
        Map<Long, String> accountMap = userIds.isEmpty() ? Map.of()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUserAccount));

        // 4. 批量查历史总消耗（账本聚合，一条 IN）：userId -> totalTokens
        Map<Long, Long> totalMap = userIds.isEmpty() ? Map.of()
                : userTokenUsageMapper.sumTokensByUserIds(userIds).stream()
                .collect(Collectors.toMap(UserTokenSumVO::getUserId,
                        UserTokenSumVO::getTotalTokens));

        // 5. 组装 VO：账号/历史总消耗缺失时兜底，保证页内每条都有值
        List<AdminQuotaUsageVO> voList = usagePage.getRecords().stream().map(u -> {
            AdminQuotaUsageVO vo = new AdminQuotaUsageVO();
            vo.setUserId(u.getUserId());
            vo.setUserAccount(accountMap.getOrDefault(u.getUserId(), "unknown"));
            vo.setUsedTokens(u.getUsedTokens());
            vo.setTotalTokens(totalMap.getOrDefault(u.getUserId(), 0L));
            return vo;
        }).toList();

        // 6. 手工组装 VO 分页页对象（与用户分页 VO 组装方式一致）
        Page<AdminQuotaUsageVO> voPage = new Page<>(pageNum, pageSize, usagePage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    // --------------私有工具------------------
    private void insertRow(Long userId, int month, long tokens) {

        UserQuotaUsage row = UserQuotaUsage.builder()
                .userId(userId)
                .month(month)
                .usedTokens(tokens)
                .build();

        // save() 走 ignoreNulls，createTime/updateTime 由数据库默认值与触发器维护（与项目其他 insert 一致）
        this.save(row);
    }

    private long getUsedTokens(Long userId) {
        UserQuotaUsage row = this.queryChain()
                .where(UserQuotaUsage::getUserId).eq(userId)
                .and(UserQuotaUsage::getMonth).eq(currentMonth())
                .one();
        return row == null || row.getUsedTokens() == null ? 0 : row.getUsedTokens();
    }

    private int currentMonth() {
        YearMonth ym = YearMonth.now();
        return ym.getYear() * 100 + ym.getMonthValue();
    }

    private boolean isUnlimited() {
        return !quotaProperties.isEnabled() || quotaProperties.getMonthlyTokens() < 0;
    }
}

