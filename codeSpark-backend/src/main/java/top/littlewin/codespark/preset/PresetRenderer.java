package top.littlewin.codespark.preset;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import top.littlewin.codespark.exception.BusinessException;
import top.littlewin.codespark.exception.ErrorCode;
import top.littlewin.codespark.exception.ErrorMessage;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预置产物渲染器：把 {{key}} 占位符替换为服务端预置的参数值（用户输入永不参与替换）
 */
@Slf4j
@Component
public class PresetRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_.]+)}}");

    /**
     * 构建占位符取值表：调色板 + 应用名 + 语言文案 + 示例数据
     */
    public Map<String, String> buildValues(PresetDefinition.Palette palette, String appName,
                                           Map<String, String> texts, Map<String, Object> sampleData) {
        Map<String, String> values = new HashMap<>();
        if (palette != null && palette.getValues() != null) {
            values.putAll(palette.getValues());
        }
        if (texts != null) {
            values.putAll(texts);
        }
        if (sampleData != null) {
            sampleData.forEach((k, v) -> values.put(k, v instanceof String s ? s : JSONUtil.toJsonStr(v)));
        }
        values.put("appName", appName == null ? "示例应用" : appName);
        return values;
    }

    /**
     * 渲染变体所有文件，返回 相对路径 -> 内容
     */
    public Map<String, String> renderFiles(PresetDefinition definition, PresetDefinition.Variant variant,
                                           Map<String, String> values) {
        List<String> files = variant.getFiles();
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, ErrorMessage.PRESET_VARIANT_EMPTY);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String file : files) {
            result.put(file, replace(loadRaw(definition.getId(), file), values));
        }
        return result;
    }

    /**
     * 渲染单个文件（如 narrative）
     */
    public String renderFile(PresetDefinition definition, String relativePath, Map<String, String> values) {
        return replace(loadRaw(definition.getId(), relativePath), values);
    }

    public String replace(String raw, Map<String, String> values) {
        if (StrUtil.isEmpty(raw)) {
            return raw;
        }
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder(raw.length());
        while (matcher.find()) {
            String value = values.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String loadRaw(String presetId, String relativePath) {
        String location = "presets/" + presetId + "/" + relativePath;
        ClassPathResource resource = new ClassPathResource(location);
        try (InputStream in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("读取预置产物失败: {}", location, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, ErrorMessage.PRESET_RESOURCE_MISSING);
        }
    }
}
