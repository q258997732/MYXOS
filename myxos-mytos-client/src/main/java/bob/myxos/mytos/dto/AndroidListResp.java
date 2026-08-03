package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安卓容器列表响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AndroidListResp extends MytosBaseResp {
    private JsonNode data;
}
