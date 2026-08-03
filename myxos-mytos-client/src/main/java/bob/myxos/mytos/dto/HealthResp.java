package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 健康检查响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HealthResp extends MytosBaseResp {
    private HealthData data;

    @Data
    public static class HealthData {
        private Boolean dockerApi;
        private Boolean pingStatus;
        private String hostIp;
    }
}
