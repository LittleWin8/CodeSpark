package top.littlewin.codespark.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 文件业务类型枚举
 */
@Getter
public enum FileBizEnum {

    AVATAR("用户头像", "avatar"),
    COVER("应用封面", "cover");

    private final String text;
    private final String value;

    FileBizEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static FileBizEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (FileBizEnum anEnum : FileBizEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
