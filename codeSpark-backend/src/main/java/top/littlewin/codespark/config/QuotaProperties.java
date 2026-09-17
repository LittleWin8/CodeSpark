package top.littlewin.codespark.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "codespark.quota")
public class QuotaProperties {
    private boolean enabled = true;
    private long monthlyTokens = 100000;
    private Set<String> countedModels = new HashSet<>();

    /**
     * 平台每月 Token 消耗总额上限（真实 AI 全部消耗口径，来自账本聚合）；-1 = 不限
     */
    private long platformMonthlyTokens = 50000000;
}