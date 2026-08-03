package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Adb shell 执行结果响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShellResp extends MytosBaseResp {
    private String data;
}
