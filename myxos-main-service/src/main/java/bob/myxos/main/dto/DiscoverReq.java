package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 网段发现任务提交请求
 */
@Data
public class DiscoverReq {

    /** CIDR 网段，如 192.168.30.0/24 */
    @NotBlank(message = "CIDR 网段不能为空")
    private String cidr;

    /** 起始端口 */
    @NotNull(message = "起始端口不能为空")
    private Integer portFrom;

    /** 结束端口 */
    @NotNull(message = "结束端口不能为空")
    private Integer portTo;
}
