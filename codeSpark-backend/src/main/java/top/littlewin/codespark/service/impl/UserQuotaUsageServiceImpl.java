package top.littlewin.codespark.service.impl;

import com.mybatisflex.core.paginate.Page;
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
        if (userId == null || tokens <= 0 || isQuotaExempt(userId)){
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
        long limit = resolveMonthlyLimit(userId);

        QuotaInfoVO info = new QuotaInfoVO();
        info.setEnabled(quotaProperties.isEnabled());
        info.setUnlimited(isUnlimited(userId));
        info.setMonthlyLimit(limit);
        info.setUsedTokens(used);
        info.setRemainingTokens(isUnlimited(userId) ? -1L
                : Math.max(0, limit - used));
        return info;
    }

    @Override
    public void checkQuota(Long userId) {
        if (!quotaProperties.isEnabled() || isUnlimited(userId)) {
            return;
        }
        ThrowUtils.throwIf(getUsedTokens(userId) >= resolveMonthlyLimit(userId), ErrorCode.OPERATION_ERROR, ErrorMessage.QUOTA_EXCEEDED);
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

        // 1. 主分页：查用户（所有用户都在，含本月未使用的）
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize));
        List<User> pageUsers = userPage.getRecords();
        List<Long> pageUserIds = pageUsers.stream().map(User::getId).toList();

        // 2. 批量补数据：当月已用 / 历史总消耗（各一条 IN，无 N+1）
        Map<Long, Long> usedMap = getUsedTokensByUserIds(pageUserIds);
        Map<Long, Long> totalMap = getTotalTokensByUserIds(pageUserIds);

        // 3. 逐用户组装看板行（未使用/无记录的补 0）
        List<AdminQuotaUsageVO> voList = pageUsers.stream()
                .map(u -> buildAdminQuotaUsageVO(u, usedMap, totalMap))
                .toList();

        // 4. 组装 VO 分页（行数与用户表完全对齐）
        Page<AdminQuotaUsageVO> voPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
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

    private boolean isUnlimited(Long userId) {
        return isQuotaExempt(userId) || quotaProperties.getMonthlyTokens() < 0;
    }

    /** 批量查当月已用（一条 IN，行缺失 = 0） */
    private Map<Long, Long> getUsedTokensByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserQuotaUsage> rows = this.queryChain()
                .where(UserQuotaUsage::getMonth).eq(currentMonth())
                .and(UserQuotaUsage::getUserId).in(userIds)
                .list();
        return rows.stream().collect(Collectors.toMap(
                UserQuotaUsage::getUserId,
                r -> r.getUsedTokens() == null ? 0L : r.getUsedTokens()));
    }

    /**
     * 批量查历史总消耗（账本聚合，一条 IN；无记录的用户不返回，由组装层补 0）
     */
    private Map<Long, Long> getTotalTokensByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userTokenUsageMapper.sumTokensByUserIds(userIds).stream()
                .collect(Collectors.toMap(UserTokenSumVO::getUserId,
                        UserTokenSumVO::getTotalTokens));
    }

    /**
     * 单个用户 → 看板行：账号 + 当月已用 + 上限/剩余（会员预留）+ 历史总消耗
     */
    private AdminQuotaUsageVO buildAdminQuotaUsageVO(User user,
                                                     Map<Long, Long> usedMap,
                                                     Map<Long, Long> totalMap) {
        AdminQuotaUsageVO vo = new AdminQuotaUsageVO();
        vo.setUserId(user.getId());
        vo.setUserAccount(user.getUserAccount());

        long used = usedMap.getOrDefault(user.getId(), 0L);
        long limit = resolveMonthlyLimit(user.getId());
        vo.setUsedTokens(used);
        vo.setMonthlyLimit(limit);
        vo.setRemainingTokens(isUnlimited(user.getId()) ? -1L : Math.max(0, limit - used));
        vo.setTotalTokens(totalMap.getOrDefault(user.getId(), 0L));
        return vo;
    }

    // --------------扩展钩子（会员/ BYOK 预留）------------------
    /**
     *判断用户是否豁免额度（BYOK 预留）
     *
     * @param userId 用户 ID
     * @return 是否豁免
     */
    private boolean isQuotaExempt(Long userId) {
        // TODO(BYOK): 查 user_api_key 表，命中则 return true
        return false;
    }

    /**
     * 解析用户每月额度上限
     * 未来优先级：BYOK(不限) > 会员等级上限 > 管理员单人覆盖(user_quota_limit 表) > 全局默认
     *
     * @param userId 用户 ID
     * @return 不同用户的月额度上限
     */
    private long resolveMonthlyLimit(Long userId) {
        // TODO(会员): 查会员等级，命中则返回对应等级上限
        // TODO(管理员覆盖): 查 user_quota_limit 表单人覆盖值
        return quotaProperties.getMonthlyTokens();
    }
}

