package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 剪贴板内容响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClipboardResp extends MytosBaseResp {
    private String data;
}
