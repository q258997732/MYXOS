package bob.myxos.main.dto;

import lombok.Data;

/**
 * 安卓实例视图对象
 * <p>
 * 设备详情页展示容器名称、运行状态等实时信息。
 */
@Data
public class AndroidInstanceVO {

    /** 容器名称 */
    private String name;

    /** 运行状态：RUNNING / STOPPED / UNKNOWN */
    private String status;

    /** 状态中文标签 */
    private String statusLabel;
}
