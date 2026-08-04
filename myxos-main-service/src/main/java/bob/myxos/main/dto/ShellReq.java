package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 同步执行 Adb（shell）命令请求 DTO
 * <p>
 * 以 JSON 请求体提交，避免裸字符串 body 被当作表单解析
 * 触发 Spring Security 防火墙参数名校验（RequestRejectedException）
 */
@Data
public class ShellReq {

    /** 安卓容器名称 */
    @NotBlank(message = "容器名称不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "容器名称包含非法字符")
    private String name;

    /** shell 命令文本 */
    @NotBlank(message = "命令不能为空")
    @Size(max = 500, message = "命令长度不能超过 500")
    private String command;
}
