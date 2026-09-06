package top.littlewin.codespark.model.enums;

import lombok.Getter;

/**
 * 构建状态枚举
 * 用于 SSE 构建事件流的命名事件：building / success / failed
 */
@Getter
public enum BuildStatusEnum {

    /** 构建中 */
    BUILDING("构建中", "building"),

    /** 构建成功 */
    SUCCESS("构建成功", "success"),

    /** 构建失败 */
    FAILED("构建失败", "failed");

    private final String text;

    private final String value;

    BuildStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }
}