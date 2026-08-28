package top.littlewin.codespark.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 对话阶段枚举
 * 用于区分 AI 是"创建应用"还是"修改应用"，从而路由到不同的系统提示词与生成逻辑。
 */
@Getter
public enum ChatStageEnum {

    /** 创建应用（无历史，首轮生成） */
    CREATE("创建应用", "create"),

    /** 修改应用（已有历史，针对已生成项目做改动） */
    MODIFY("修改应用", "modify");

    private final String text;

    private final String value;

    ChatStageEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static ChatStageEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ChatStageEnum anEnum : ChatStageEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}