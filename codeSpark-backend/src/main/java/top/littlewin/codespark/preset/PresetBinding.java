package top.littlewin.codespark.preset;

/**
 * 一次预置生成选定的变体与参数（落库到 app_preset 的旁路信息）
 */
public record PresetBinding(String presetId, String variantId, String paletteId,
                            String name, String sampleDataId, String locale) {
}
