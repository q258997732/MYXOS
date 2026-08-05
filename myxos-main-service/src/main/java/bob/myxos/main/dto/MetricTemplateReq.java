package bob.myxos.main.dto;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class MetricTemplateReq {
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 128, message = "模板名称长度不能超过128")
    private String name;
    @NotBlank(message = "目标类型不能为空")
    @Pattern(regexp = "^(HOST|ANDROID_INSTANCE)$", message = "目标类型不合法")
    private String targetType;
    private Integer enabled = 1;
    @Valid @NotEmpty(message = "模板项不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "目录指标不能为空") private Long metricCatalogId;
        private Integer enabled = 1;
        @NotNull @Min(value = 15, message = "采集频率不能小于15秒") @Max(value = 86400, message = "采集频率不能大于86400秒")
        private Integer defaultIntervalSec;
        private String enumOptions;
    }
}
