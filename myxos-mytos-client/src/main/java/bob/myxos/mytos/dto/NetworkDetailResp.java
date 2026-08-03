package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网络对象明细响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NetworkDetailResp extends MytosBaseResp {
    private JsonNode data;
}
