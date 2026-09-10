package top.littlewin.codespark.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AdminQuotaUsageVO implements Serializable {
    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userAccount;

    /**
     * 已使用额度
     */
    private Long usedTokens;

    /**
     * 总消耗 Tokens
     */
    private Long totalTokens;

    @Serial
    private static final long serialVersionUID = 1L;
}
