package top.littlewin.codespark.model.dto.quota;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员重置用户额度请求
 */
@Data
public class ResetQuotaRequest implements Serializable {

    /**
     * 用户 ID
     */
    private Long userId;
}
