package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 设备手动操作请求 DTO
 */
@Data
public class DeviceOpReq {

    /** 操作码，对应 OperationCode 枚举 */
    @NotBlank(message = "操作码不能为空")
    @Pattern(regexp = "^(REBOOT|ADB_ON|ADB_OFF|KEEPALIVE_ON|KEEPALIVE_OFF|SET_CLIPBOARD|CLEAR_PROXY|SET_PROXY|UPLOAD_FILE|REFRESH_LOC|SET_FINGERPRINT|SET_LANGUAGE|SET_PROXY_FILTER)$",
            message = "不支持的操作码")
    private String operationCode;

    /** 操作参数（JSON 字符串，可选） */
    private String params;
}
