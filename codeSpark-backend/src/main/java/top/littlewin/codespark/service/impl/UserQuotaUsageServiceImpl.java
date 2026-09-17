package top.littlewin.codespark.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import top.littlewin.codespark.config.QuotaProperties;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;
import top.littlewin.codespark.exception.ThrowUtils;
import top.littlewin.codespark.mapper.UserTokenUsageMapper;
import top.littlewin.codespark.model.entity.UserQuotaUsage;
import top.littlewin.codespark.mapper.UserQuotaUsageMapper;
import top.littlewin.codespark.model.vo.AdminQuotaUsageVO;
import top.littlewin.codespark.model.vo.QuotaInfoVO;
import top.littlewin.codespark.model.vo.UserTokenSumVO;
import top.littlewin.codespark.service.UserQuotaUsageService;
import org.springframework.stereotype.Service;

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
        if (!quotaProperties.isEnabled()) {
            return;
        }
        // 1. 平台月总额度：账本当月真实消耗总和（口径 B），超限阻断所有真实生成
        long platformUsed = getPlatformUsedTokens();
        long platformLimit = quotaProperties.getPlatformMonthlyTokens();
        if (platformLimit >= 0 && platformUsed >= platformLimit) {
            log.warn("平台月额度已超限，阻断生成: platformUsed={}, limit={}", platformUsed, platformLimit);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, ErrorMessage.PLATFORM_QUOTA_EXCEEDED);
        }

        // 2. 用户个人额度校验
        if (isUnlimited(userId)) {
            return;
        }
        ThrowUtils.throwIf(getUsedTokens(userId) >= resolveMonthlyLimit(userId), ErrorCode.OPERATION_ERROR, ErrorMessage.QUOTA_EXCEEDED);
    }

    /**
     * 平台月度额度概况（月上限 + 当月真实消耗总额，管理看板展示用）
     */
    @Override
    public QuotaInfoVO getPlatformQuota() {
        long limit = quotaProperties.getPlatformMonthlyTokens();
        long used = getPlatformUsedTokens();
        QuotaInfoVO info = new QuotaInfoVO();
        info.setEnabled(quotaProperties.isEnabled());
        info.setUnlimited(limit < 0);
        info.setMonthlyLimit(limit);
        info.setUsedTokens(used);
        info.setRemainingTokens(limit < 0 ? -1L : Math.max(0, limit - used));
        return info;
    }

    private long getPlatformUsedTokens() {
        Long used = userTokenUsageMapper.selectPlatformUsedTokens(currentMonth());
        return used == null ? 0 : used;
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

    /**
     * 用户消耗排行分页
     * <p>排序白名单：本月已用（默认）/ 历史总消耗，升降序均可；SQL 两表联查完成排序，
     * 所有用户都在结果中（无消耗的排最后，数值补 0）。
     */
    @Override
    public Page<AdminQuotaUsageVO> adminPageUsage(long pageNum, long pageSize,
                                                  String sortField, String sortOrder) {
        // 1. 排序白名单：本月已用（默认）/ 历史总消耗
        boolean byUsed = !"totalTokens".equals(sortField);
        boolean asc = "ascend".equals(sortOrder);
        long offset = (pageNum - 1) * pageSize;
        int month = currentMonth();

        // 2. SQL 排行分页（两表联查，按列排序）
        List<AdminQuotaUsageVO> voList = byUsed
                ? userTokenUsageMapper.selectRankedUsersByUsed(month, asc, pageSize, offset)
                : userTokenUsageMapper.selectRankedUsersByTotal(asc, pageSize, offset);
        long total = userTokenUsageMapper.countRankedUsers();

        // 3. 批量补数据：账本当月消耗（ByTotal 时 SQL 未查）/ 历史总消耗（各一条 IN，无 N+1）
        List<Long> pageUserIds = voList.stream().map(AdminQuotaUsageVO::getUserId).toList();
        Map<Long, Long> ledgerMonthMap = byUsed
                ? Map.of()
                : userTokenUsageMapper.selectMonthTokensByUserIds(month, pageUserIds).stream()
                    .collect(Collectors.toMap(UserTokenSumVO::getUserId, UserTokenSumVO::getTotalTokens));
        Map<Long, Long> totalMap = userTokenUsageMapper.sumTokensByUserIds(pageUserIds).stream()
                .collect(Collectors.toMap(UserTokenSumVO::getUserId, UserTokenSumVO::getTotalTokens));

        // 4. 组装：本月已用 = 账本当月（真实消耗，管理员重置不影响）；剩余 = 额度计数器口径（重置可恢复）
        Map<Long, Long> quotaUsedMap = getUsedTokensByUserIds(pageUserIds);
        for (AdminQuotaUsageVO vo : voList) {
            long ledgerUsed = byUsed
                    ? (vo.getUsedTokens() == null ? 0 : vo.getUsedTokens())
                    : ledgerMonthMap.getOrDefault(vo.getUserId(), 0L);
            long limit = resolveMonthlyLimit(vo.getUserId());
            long quotaUsed = quotaUsedMap.getOrDefault(vo.getUserId(), 0L);
            vo.setUsedTokens(ledgerUsed);
            vo.setMonthlyLimit(limit);
            vo.setRemainingTokens(isUnlimited(vo.getUserId()) ? -1L : Math.max(0, limit - quotaUsed));
            // 历史总消耗：ByUsed 分支 SQL 未查 → 从批量聚合补；ByTotal 分支 SQL 已带
            vo.setTotalTokens(byUsed
                    ? totalMap.getOrDefault(vo.getUserId(), 0L)
                    : (vo.getTotalTokens() == null ? 0L : vo.getTotalTokens()));
        }

        Page<AdminQuotaUsageVO> voPage = new Page<>(pageNum, pageSize, total);
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
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                UserQuotaUsage::getUserId,
                r -> r.getUsedTokens() == null ? 0L : r.getUsedTokens()));
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
