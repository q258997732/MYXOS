package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/** 执行受控指标命令并读取枚举候选的请求。 */
@Data
public class ThresholdMetricOptionExecuteReq {

    @NotNull(message = "设备 ID 不能为空")
    private Long deviceId;

    @NotBlank(message = "安卓实例名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,128}$", message = "安卓实例名称格式不合法")
    private String androidName;

    @NotBlank(message = "指标编码不能为空")
    private String metricCode;

    @NotBlank(message = "应用包名不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$", message = "应用包名格式不合法")
    private String appPackage;
}
