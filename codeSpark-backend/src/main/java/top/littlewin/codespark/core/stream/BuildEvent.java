package top.littlewin.codespark.core.stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.littlewin.codespark.model.enums.BuildStatusEnum;

import java.time.LocalDateTime;

/**
 * 构建状态事件（SSE 构建事件流的数据载荷）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildEvent {

    /** 应用 ID */
    private Long appId;

    /** 构建状态 */
    private BuildStatusEnum status;

    /** 状态说明（构建失败时携带原因） */
    private String message;

    /** 事件时间 */
    private LocalDateTime timestamp;
}