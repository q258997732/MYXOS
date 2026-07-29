package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 设备更新请求 DTO
 * 仅允许修改名称、分组、状态、备注；IP/端口/模式创建后不可修改
 */
@Data
public class DeviceUpdateReq {

    /** 设备名称 */
    @Size(max = 64, message = "设备名称长度不能超过 64")
    private String name;

    /** 分组 ID */
    private Long groupId;

    /** 状态：ONLINE / OFFLINE / UNKNOWN / DISABLED */
    @Pattern(regexp = "^(ONLINE|OFFLINE|UNKNOWN|DISABLED)$", message = "状态不合法")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
