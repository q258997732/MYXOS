package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class MetricCatalogUpdateReq {
    @NotNull(message = "默认采集频率不能为空")
    @Min(value = 15, message = "默认采集频率不能小于15秒")
    @Max(value = 86400, message = "默认采集频率不能大于86400秒")
    private Integer defaultIntervalSec;
}
