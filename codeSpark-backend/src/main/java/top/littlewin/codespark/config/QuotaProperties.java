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
    private long monthlyTokens = 200000;
    private Set<String> countedModels = new HashSet<>();
}