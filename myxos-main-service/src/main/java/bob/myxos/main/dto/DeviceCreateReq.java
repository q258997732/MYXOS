package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 设备创建请求 DTO
 */
@Data
public class DeviceCreateReq {

    /** 设备名称 */
    @Size(max = 64, message = "设备名称长度不能超过 64")
    private String name;

    /** 设备 IP（IPv4） */
    @NotBlank(message = "设备 IP 不能为空")
    @Pattern(regexp = "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$",
            message = "设备 IP 格式不正确")
    private String ip;

    /** 设备端口 */
    @NotNull(message = "设备端口不能为空")
    @Min(value = 1, message = "端口最小为 1")
    @Max(value = 65535, message = "端口最大为 65535")
    private Integer port;

    /** 网络模式：BRIDGE / NAT */
    @NotBlank(message = "网络模式不能为空")
    @Pattern(regexp = "^(BRIDGE|NAT)$", message = "网络模式仅支持 BRIDGE 或 NAT")
    private String mode;

    /** 分组 ID（可选） */
    private Long groupId;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
