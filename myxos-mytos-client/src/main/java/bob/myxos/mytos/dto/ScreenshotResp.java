package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 截图响应
 *
 * <p>
 * 设备端 {@code data} 为对象（含 url），实际图片 Base64 放在 {@code message/msg} 字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScreenshotResp extends MytosBaseResp {
    private JsonNode data;
}
