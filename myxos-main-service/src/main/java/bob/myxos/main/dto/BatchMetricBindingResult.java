package bob.myxos.main.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchMetricBindingResult {
    private List<TargetResult> succeeded = new ArrayList<TargetResult>();
    private List<TargetResult> failed = new ArrayList<TargetResult>();

    @Data
    public static class TargetResult {
        private Long deviceId;
        private String androidName;
        private String reason;
    }
}
