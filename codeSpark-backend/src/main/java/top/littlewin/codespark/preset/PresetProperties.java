package top.littlewin.codespark.preset;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 预置示例应用配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "codespark.preset")
public class PresetProperties {
    /**
     * 总开关：关闭后所有入口回落真实 AI 生成
     */
    private boolean enabled = true;

    /**
     * 回放时每个文本分片的字符数
     */
    private int replayChunkSize = 40;

    /**
     * 回放分片间隔（毫秒）
     */
    private long replayIntervalMillis = 20;

    /**
     * 同一用户同一预置应用连续命中的排除次数上限
     */
    private int selectRetry = 5;
}
