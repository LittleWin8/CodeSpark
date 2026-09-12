package top.littlewin.codespark.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预置示例应用绑定（旁路表，仅内部路由/分析用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "app_preset", schema = "public")
public class AppPreset implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.None)
    @Column("appId")
    private Long appId;

    @Column("presetId")
    private String presetId;

    @Column("variantId")
    private String variantId;

    @Column("paletteId")
    private String paletteId;

    @Column("appName")
    private String appName;

    @Column("sampleDataId")
    private String sampleDataId;

    private String locale;

    private Integer used;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;
}
