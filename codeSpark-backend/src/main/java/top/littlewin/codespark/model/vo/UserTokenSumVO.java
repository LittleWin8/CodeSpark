package top.littlewin.codespark.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserTokenSumVO implements Serializable {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 消耗总 Tokens
     */
    private Long totalTokens;

    @Serial
    private static final long serialVersionUID = 1L;
}