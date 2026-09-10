package top.littlewin.codespark.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import top.littlewin.codespark.model.entity.UserTokenUsage;
import top.littlewin.codespark.mapper.UserTokenUsageMapper;
import top.littlewin.codespark.service.UserTokenUsageService;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * 用户 Token 消耗账本（按用户/模型/月份累计，永不重置） 服务层实现。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Slf4j
@Service
public class UserTokenUsageServiceImpl extends ServiceImpl<UserTokenUsageMapper, UserTokenUsage>  implements UserTokenUsageService{

    @Resource
    private UserTokenUsageMapper userTokenUsageMapper;

    @Override
    public void recordUsage(Long userId, String modelName, long input, long output, long total) {

        // 1. 参数校验
        if (userId == null || StrUtil.isBlank(modelName) || total <= 0) {
            return;
        }

        // 2. 获取年月，如 2026.9 -> 202609
        YearMonth ym = YearMonth.now();
        int month = ym.getYear() * 100 + ym.getMonthValue();

        // 3. 数据落库
        try {
            int updated = userTokenUsageMapper.incrTokens(userId, modelName, month, input, output, total);

            // 若更新失败，则插入数据
            if (updated == 0) {
                insertRow(userId, modelName, month, input, output, total);
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个线程同时首插，唯一键冲突 → 降级为累加
            userTokenUsageMapper.incrTokens(userId, modelName, month, input, output, total);
        } catch (Exception e) {
            log.error("Token 账本落库失败: userId={}, model={}", userId, modelName, e);
        }
    }

    @Override
    public Long sumTokensByUserId(Long userId) {
        return userTokenUsageMapper.sumTokensByUserId(userId);
    }

    private void insertRow(Long userId, String modelName, Integer month,
                           long input, long output, long total) {
        UserTokenUsage row = UserTokenUsage.builder()
                .userId(userId)
                .modelName(modelName)
                .month(month)
                .inputTokens(input)
                .outputTokens(output).totalTokens(total)
                .build();
        // save() 走 ignoreNulls，createTime/updateTime 由数据库默认值与触发器维护（与项目其他 insert 一致）
        this.save(row);
    }

}
