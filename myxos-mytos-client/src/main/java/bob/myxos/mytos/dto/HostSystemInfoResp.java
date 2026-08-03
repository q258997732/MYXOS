package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 主机系统信息响应
 * <p>
 * 由于不同主机版本返回字段可能不同，使用 {@link JsonNode} 承载动态结构。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HostSystemInfoResp extends MytosBaseResp {
    private JsonNode data;
}
