package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 主机硬件配置响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HardwareCfgResp extends MytosBaseResp {
    private JsonNode data;
}
