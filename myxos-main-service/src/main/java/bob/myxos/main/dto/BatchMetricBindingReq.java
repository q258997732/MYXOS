package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

@Data
public class BatchMetricBindingReq {
    @NotBlank
    @Pattern(regexp = "HOST|ANDROID_INSTANCE")
    private String targetType;
    @Valid
    @NotEmpty
    private List<Target> targets;

    @Data
    public static class Target {
        @NotNull
        private Long deviceId;
        private String androidName;
        @Valid
        @NotEmpty
        private List<MetricBindingReq.Item> items;
    }
}
