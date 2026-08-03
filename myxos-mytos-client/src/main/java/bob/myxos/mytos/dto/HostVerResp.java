package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 主机版本响应
 *
 * <p>
 * 设备端将版本号放在 {@code data} 字段，message/msg 仅为成功提示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HostVerResp extends MytosBaseResp {
    private String data;
}
