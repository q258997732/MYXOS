package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安卓实例详情响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AndroidDetailResp extends MytosBaseResp {
    private JsonNode data;
}
