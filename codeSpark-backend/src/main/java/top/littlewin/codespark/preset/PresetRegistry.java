package top.littlewin.codespark.preset;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 预置应用清单注册表：启动时扫描 resources/presets/{id}/manifest.json
 */
@Slf4j
@Component
public class PresetRegistry {

    private final Map<String, PresetDefinition> definitions = new LinkedHashMap<>();

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PresetProperties properties;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("预置示例应用已关闭（codespark.preset.enabled=false）");
            return;
        }
        try {
            org.springframework.core.io.Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:presets/*/manifest.json");
            for (org.springframework.core.io.Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    PresetDefinition definition = objectMapper.readValue(in, PresetDefinition.class);
                    if (definition.getId() == null || definition.getVariants() == null
                            || definition.getVariants().isEmpty()) {
                        log.warn("跳过非法预置清单: {}", resource.getFilename());
                        continue;
                    }
                    definitions.put(definition.getId(), definition);
                } catch (Exception e) {
                    log.error("加载预置清单失败: {}", resource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.error("扫描预置清单失败", e);
        }
        log.info("预置示例应用加载完成: {}", definitions.keySet());
    }

    public PresetDefinition get(String id) {
        return definitions.get(id);
    }
}
