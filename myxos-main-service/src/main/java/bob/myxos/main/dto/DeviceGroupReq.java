package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 设备分组创建/更新请求 DTO
 */
@Data
public class DeviceGroupReq {

    /** 分组名称 */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 64, message = "分组名称长度不能超过 64")
    private String name;

    /** 父分组 ID，0 表示根分组 */
    private Long parentId;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
