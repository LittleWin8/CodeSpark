package top.littlewin.codespark.preset;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 预置示例应用清单（对应 resources/presets/{id}/manifest.json）
 */
@Data
public class PresetDefinition {

    private String id;
    private int version = 1;
    private boolean enabled = true;

    /**
     * html / multi_file / vue
     */
    private String codeGenType;

    /** locale -> 展示标签（列表接口用） */
    private Map<String, String> label;

    /** locale -> 初始提示词（用于命中校验，需与前端发送完全一致） */
    private Map<String, String> initPrompt;

    private List<Variant> variants;

    private ParamPools paramPools;

    @Data
    public static class Variant {
        private String id;
        /** 相对 presets/{id}/ 的目录 */
        private String dir;
        /** 相对 presets/{id}/ 的叙述文件（回放时作为 AI 说明文字，可含占位符） */
        private String narrative;
        /** 相对 presets/{id}/ 的文件列表（jar 内无法列目录，必须显式声明） */
        private List<String> files;
    }

    @Data
    public static class ParamPools {
        private List<Palette> palettes;
        /** locale -> 应用名池 */
        private Map<String, List<String>> names;
        /** locale -> 文案键值（产物内 {{key}} 占位） */
        private Map<String, Map<String, String>> texts;
        private List<SampleData> sampleData;
    }

    @Data
    public static class Palette {
        private String id;
        private Map<String, String> values;
    }

    @Data
    public static class SampleData {
        private String id;
        private Map<String, Object> values;
    }

    /**
     * 某语言的初始提示词
     */
    public String promptOf(String locale) {
        if (initPrompt == null) {
            return null;
        }
        String prompt = initPrompt.get(locale);
        return prompt != null ? prompt : initPrompt.values().stream().findFirst().orElse(null);
    }

    public String codeGenTypeOrDefault() {
        return codeGenType != null ? codeGenType : "html";
    }
}
