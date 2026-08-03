package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 截图响应
 * <p>
 * 设备端可能直接返回图片二进制或 Base64 字符串，这里按字符串承载。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScreenshotResp extends MytosBaseResp {
    private String data;
}
