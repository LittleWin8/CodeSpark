package top.littlewin.codespark.model.dto.quota;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.littlewin.codespark.common.PageRequest;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class QuotaUsageQueryRequest extends PageRequest implements Serializable {
    // 暂无筛选条件，留扩展位

    @Serial
    private static final long serialVersionUID = 1L;
}