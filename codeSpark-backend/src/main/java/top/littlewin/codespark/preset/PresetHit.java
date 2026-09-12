package top.littlewin.codespark.preset;

import top.littlewin.codespark.model.entity.AppPreset;

/**
 * 预置命中结果：绑定信息 + 清单定义
 */
public record PresetHit(AppPreset row, PresetDefinition definition) {
}
