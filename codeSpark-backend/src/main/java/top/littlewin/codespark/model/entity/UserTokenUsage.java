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
 * 用户 Token 消耗账本（按用户/模型/月份累计，永不重置） 实体类。
 *
 * @author <a href="https://github.com/LittleWin8">小稳</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "user_token_usage", schema = "public")
public class UserTokenUsage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("userId")
    private Long userId;

    @Column("modelName")
    private String modelName;

    private Integer month;

    @Column("inputTokens")
    private Long inputTokens;

    @Column("outputTokens")
    private Long outputTokens;

    @Column("totalTokens")
    private Long totalTokens;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
