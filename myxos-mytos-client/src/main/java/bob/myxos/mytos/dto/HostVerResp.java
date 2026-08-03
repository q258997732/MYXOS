package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 主机版本响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HostVerResp extends MytosBaseResp {
    private String msg;
}
