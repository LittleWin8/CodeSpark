package top.littlewin.codespark.preset;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import top.littlewin.codespark.ai.model.message.AiResponseMessage;
import top.littlewin.codespark.ai.model.message.AiThinkingMessage;
import top.littlewin.codespark.ai.model.message.StreamMessage;
import top.littlewin.codespark.ai.model.message.StreamMessageTypeEnum;
import top.littlewin.codespark.ai.model.message.ToolExecutedMessage;
import top.littlewin.codespark.ai.model.message.ToolRequestMessage;
import top.littlewin.codespark.constant.AppConstant;
import top.littlewin.codespark.core.builder.VueProjectBulider;
import top.littlewin.codespark.core.stream.BuildEventPublisher;
import top.littlewin.codespark.mapper.AppPresetMapper;
import top.littlewin.codespark.model.entity.AppPreset;
import top.littlewin.codespark.model.entity.User;
import top.littlewin.codespark.model.enums.BuildStatusEnum;
import top.littlewin.codespark.service.ChatHistoryService;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预置示例应用服务：命中后不调用 AI，但产物/落库/回放与真实生成一致
 */
@Slf4j
@Service
public class PresetService {

    @Resource
    private PresetProperties properties;

    @Resource
    private PresetRegistry registry;

    @Resource
    private PresetRenderer renderer;

    @Resource
    private PresetFileWriter fileWriter;

    @Resource
    private PresetSseReplayer replayer;

    @Resource
    private AppPresetMapper appPresetMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private BuildEventPublisher buildEventPublisher;

    @Resource
    private VueProjectBulider vueProjectBulider;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 创建应用阶段解析：presetId 合法且提示词与清单完全一致才命中
     */
    public PresetDefinition resolveForCreate(String prompt, String presetId) {
        if (StrUtil.isBlank(presetId) || StrUtil.isBlank(prompt)) {
            return null;
        }
        PresetDefinition definition = registry.get(presetId);
        if (definition == null || !definition.isEnabled()) {
            return null;
        }
        return matchLocale(definition, prompt) != null ? definition : null;
    }

    public String resolveLocale(PresetDefinition definition, String prompt) {
        String locale = matchLocale(definition, prompt);
        return locale != null ? locale : "zh-CN";
    }

    private String matchLocale(PresetDefinition definition, String prompt) {
        if (definition.getInitPrompt() == null || prompt == null) {
            return null;
        }
        String trimmed = prompt.trim();
        for (Map.Entry<String, String> entry : definition.getInitPrompt().entrySet()) {
            if (entry.getValue() != null && entry.getValue().trim().equals(trimmed)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 选择本次产物：布局变体轮询（复用 Redis key 存上次 variantId），配色/应用名/示例数据随机
     */
    public PresetBinding choose(PresetDefinition definition, String locale, Long userId) {
        String language = StrUtil.blankToDefault(locale, "zh-CN");
        List<PresetDefinition.Variant> variants = definition.getVariants();

        String lastVariantId = userId == null ? null
                : stringRedisTemplate.opsForValue().get(lastKey(userId, definition.getId()));
        int lastIndex = -1;
        for (int i = 0; i < variants.size(); i++) {
            if (variants.get(i).getId().equals(lastVariantId)) {
                lastIndex = i;
                break;
            }
        }
        int variantIndex = lastIndex < 0
                ? RandomUtil.randomInt(variants.size())
                : (lastIndex + 1) % variants.size();
        PresetDefinition.Variant variant = variants.get(variantIndex);

        PresetDefinition.ParamPools pools = definition.getParamPools();
        String paletteId = null;
        String name = definition.getLabel() == null ? "示例应用"
                : StrUtil.blankToDefault(definition.getLabel().get(language), "示例应用");
        String sampleId = null;
        if (pools != null) {
            if (pools.getPalettes() != null && !pools.getPalettes().isEmpty()) {
                paletteId = pools.getPalettes()
                        .get(RandomUtil.randomInt(pools.getPalettes().size())).getId();
            }
            if (pools.getNames() != null) {
                List<String> names = pools.getNames().get(language);
                if (names == null || names.isEmpty()) {
                    names = pools.getNames().values().stream().findFirst().orElse(List.of());
                }
                if (!names.isEmpty()) {
                    name = names.get(RandomUtil.randomInt(names.size()));
                }
            }
            if (pools.getSampleData() != null && !pools.getSampleData().isEmpty()) {
                sampleId = pools.getSampleData()
                        .get(RandomUtil.randomInt(pools.getSampleData().size())).getId();
            }
        }

        if (userId != null) {
            stringRedisTemplate.opsForValue().set(lastKey(userId, definition.getId()),
                    variant.getId(), Duration.ofDays(30));
        }
        return new PresetBinding(definition.getId(), variant.getId(), paletteId, name, sampleId, language);
    }

    public void saveBinding(Long appId, PresetBinding binding) {
        AppPreset row = AppPreset.builder()
                .appId(appId)
                .presetId(binding.presetId())
                .variantId(binding.variantId())
                .paletteId(binding.paletteId())
                .appName(binding.name())
                .sampleDataId(binding.sampleDataId())
                .locale(binding.locale())
                .used(0)
                .build();
        appPresetMapper.insertSelective(row);
    }

    /**
     * 对话阶段命中判定：绑定未用 + 无 AI 历史 + 提示词未被改动
     */
    public PresetHit findHit(Long appId, String message) {
        if (!properties.isEnabled() || appId == null) {
            return null;
        }
        AppPreset row = appPresetMapper.selectOneByQuery(
                QueryWrapper.create().where(AppPreset::getAppId).eq(appId));
        if (row == null || (row.getUsed() != null && row.getUsed() == 1)) {
            return null;
        }
        PresetDefinition definition = registry.get(row.getPresetId());
        if (definition == null || !definition.isEnabled()) {
            return null;
        }
        if (chatHistoryService.hasAiChatMessage(appId)) {
            return null;
        }
        if (matchLocale(definition, message) == null) {
            return null;
        }
        return new PresetHit(row, definition);
    }

    public void markUsed(Long appId) {
        UpdateChain.of(AppPreset.class)
                .set(AppPreset::getUsed, 1)
                .where(AppPreset::getAppId).eq(appId)
                .update();
    }

    /**
     * 生成预置事件流：落盘 + 回放（VUE 完成后触发构建）
     */
    public Flux<StreamMessage> stream(PresetHit hit, User loginUser) {
        AppPreset row = hit.row();
        PresetDefinition definition = hit.definition();
        String codeGenType = definition.codeGenTypeOrDefault();

        PresetDefinition.Variant variant = findVariant(definition, row.getVariantId());
        PresetDefinition.Palette palette = findPalette(definition, row.getPaletteId());
        Map<String, String> texts = resolveTexts(definition, row.getLocale());
        Map<String, Object> sampleData = findSampleData(definition, row.getSampleDataId());

        Map<String, String> values = renderer.buildValues(palette, row.getAppName(), texts, sampleData);
        Map<String, String> rendered = renderer.renderFiles(definition, variant, values);
        // 清单里的路径含变体目录前缀（variants/vN/），落盘与回放路径需剥离，保证 package.json/index.html 在项目根
        Map<String, String> files = stripVariantPrefix(rendered, variant.getDir());
        fileWriter.write(codeGenType, row.getAppId(), files);

        List<StreamMessage> events = new ArrayList<>();
        events.add(new AiThinkingMessage("正在根据需求规划页面结构与视觉方案..."));

        StringBuilder response = new StringBuilder(
                renderer.renderFile(definition, variant.getNarrative(), values));
        if (!"vue".equals(codeGenType)) {
            files.forEach((relativePath, content) -> response.append("\n\n```")
                    .append(StrUtil.nullToEmpty(FileUtil.extName(relativePath)))
                    .append("\n").append(content).append("\n```"));
        }
        events.add(new AiResponseMessage(response.toString()));

        if ("vue".equals(codeGenType)) {
            files.forEach((relativePath, content) -> {
                events.add(toolRequest(relativePath));
                events.add(toolExecuted(relativePath, content));
            });
        }

        Flux<StreamMessage> flux = replayer.replay(events);
        if ("vue".equals(codeGenType)) {
            flux = flux.doOnComplete(() -> triggerBuild(row.getAppId()));
        }
        meterRegistry.counter("preset_generations_total",
                        "presetId", definition.getId(),
                        "variantId", StrUtil.nullToEmpty(row.getVariantId()))
                .increment();
        log.info("预置示例生成: appId={}, presetId={}, variant={}, palette={}, name={}",
                row.getAppId(), definition.getId(), row.getVariantId(), row.getPaletteId(), row.getAppName());
        return flux;
    }

    private ToolRequestMessage toolRequest(String relativePath) {
        ToolRequestMessage message = new ToolRequestMessage();
        message.setType(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        message.setId(RandomUtil.randomString(16));
        message.setName("writeFile");
        message.setArguments(JSONUtil.createObj().set("relativeFilePath", relativePath).toString());
        return message;
    }

    private ToolExecutedMessage toolExecuted(String relativePath, String content) {
        ToolExecutedMessage message = new ToolExecutedMessage();
        message.setType(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        message.setId(RandomUtil.randomString(16));
        message.setName("writeFile");
        message.setArguments(JSONUtil.createObj()
                .set("relativeFilePath", relativePath)
                .set("content", content)
                .toString());
        message.setResult("success");
        return message;
    }

    private void triggerBuild(Long appId) {
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_" + appId;
        buildEventPublisher.publish(appId, BuildStatusEnum.BUILDING, null);
        vueProjectBulider.buildProjectAsync(projectPath, result ->
                buildEventPublisher.publish(appId,
                        result.isSuccess() ? BuildStatusEnum.SUCCESS : BuildStatusEnum.FAILED,
                        result.getMessage()));
    }

    private String lastKey(Long userId, String presetId) {
        return "preset:last:" + userId + ":" + presetId;
    }

    private PresetDefinition.Variant findVariant(PresetDefinition definition, String variantId) {
        for (PresetDefinition.Variant variant : definition.getVariants()) {
            if (variant.getId().equals(variantId)) {
                return variant;
            }
        }
        return definition.getVariants().get(0);
    }

    private PresetDefinition.Palette findPalette(PresetDefinition definition, String paletteId) {
        PresetDefinition.ParamPools pools = definition.getParamPools();
        if (pools == null || pools.getPalettes() == null || pools.getPalettes().isEmpty()) {
            return null;
        }
        for (PresetDefinition.Palette palette : pools.getPalettes()) {
            if (palette.getId().equals(paletteId)) {
                return palette;
            }
        }
        return pools.getPalettes().get(0);
    }

    private Map<String, String> resolveTexts(PresetDefinition definition, String locale) {
        PresetDefinition.ParamPools pools = definition.getParamPools();
        if (pools == null || pools.getTexts() == null) {
            return Map.of();
        }
        Map<String, String> texts = pools.getTexts().get(locale);
        if (texts != null) {
            return texts;
        }
        return pools.getTexts().values().stream().findFirst().orElse(Map.of());
    }

    private Map<String, Object> findSampleData(PresetDefinition definition, String sampleDataId) {
        PresetDefinition.ParamPools pools = definition.getParamPools();
        if (pools == null || pools.getSampleData() == null || pools.getSampleData().isEmpty()) {
            return Map.of();
        }
        for (PresetDefinition.SampleData sample : pools.getSampleData()) {
            if (sample.getId().equals(sampleDataId)) {
                return sample.getValues() == null ? Map.of() : sample.getValues();
            }
        }
        Map<String, Object> values = pools.getSampleData().get(0).getValues();
        return values == null ? Map.of() : values;
    }

    /**
     * 去掉清单文件路径中的变体目录前缀
     */
    private Map<String, String> stripVariantPrefix(Map<String, String> files, String variantDir) {
        String prefix = StrUtil.nullToEmpty(variantDir);
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String path = entry.getKey();
            result.put(path.startsWith(prefix) ? path.substring(prefix.length()) : path, entry.getValue());
        }
        return result;
    }
}
