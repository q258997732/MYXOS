package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class BatchMetricBindingReq {
    @NotNull
    private String targetType;
    @Valid
    @NotEmpty
    private List<Target> targets;
    @Valid
    @NotEmpty
    private List<MetricBindingReq.Item> items;

    @Data
    public static class Target {
        @NotNull
        private Long deviceId;
        private String androidName;
    }
}
