package bob.myxos.main.dto;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

@Data
public class MetricBindingReq {
    private List<Long> templateIds;
    @Valid private List<Item> items;

    @Data
    public static class Item {
        @NotBlank(message = "指标编码不能为空") private String metricCode;
        private Integer enabled;
        @Min(15) @Max(86400) private Integer intervalSec;
    }
}
