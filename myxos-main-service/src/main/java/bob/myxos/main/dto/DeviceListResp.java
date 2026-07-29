package bob.myxos.main.dto;

import bob.myxos.domain.entity.Device;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备列表响应 DTO
 * 在 Device 实体基础上附加当前 FIRING 告警数量
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceListResp extends Device {

    /** 当前 FIRING 状态的告警数量 */
    private Long alarmCount;
}
