package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.Map;

/**
 * 设备手动操作请求 DTO
 */
@Data
public class DeviceOpReq {

    /** 操作码，对应 OperationCode 枚举 */
    @NotBlank(message = "操作码不能为空")
    @Pattern(regexp = "^(REBOOT_HOST|RUN_ANDROID|STOP_ANDROID|REBOOT_ANDROID|RESET_ANDROID|RENAME_ANDROID|SET_CLIPBOARD|GET_CLIPBOARD|SET_LANGUAGE|REFRESH_LOCATION|SCREENSHOT|SHELL_ADB)$",
            message = "不支持的操作码")
    private String operationCode;

    /** 操作参数（Map，可选；持久化前序列化为 JSON 字符串） */
    private Map<String, Object> params;
}
