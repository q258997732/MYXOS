package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安卓启动状态响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BootStatusResp extends MytosBaseResp {
    private JsonNode data;
}
