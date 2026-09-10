package top.littlewin.codespark.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;

import java.io.Serial;
import java.time.LocalDateTime;

import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户月额度计数器（仅计费模型消耗，管理员可重置） 实体类。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "user_quota_usage", schema = "public")
public class UserQuotaUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("userId")
    private Long userId;

    private Integer month;

    @Column("usedTokens")
    private Long usedTokens;

    @Column("resetTime")
    private LocalDateTime resetTime;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
