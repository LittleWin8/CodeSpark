package top.littlewin.codespark.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class QuotaInfoVO implements Serializable {

    /**
     * 额度功能开关
     */
    private Boolean enabled;

    /**
     * 是否不限额
     */
    private Boolean unlimited;

    /**
     * 每月上限
     */
    private Long monthlyLimit;

    /**
     * 本月已消耗
     */
    private Long usedTokens;

    /**
     * 剩余额度
     */
    private Long remainingTokens;

    @Serial
    private static final long serialVersionUID = 1L;
}