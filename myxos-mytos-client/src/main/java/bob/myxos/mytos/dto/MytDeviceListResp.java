package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 局域网在线 MYTOS 设备列表响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MytDeviceListResp extends MytosBaseResp {
    private JsonNode data;
}
