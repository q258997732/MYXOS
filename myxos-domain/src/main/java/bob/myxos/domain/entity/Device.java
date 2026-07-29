package bob.myxos.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备实体
 */
@Data
@TableName("device")
public class Device {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备名称 */
    private String name;

    /** 设备 IP */
    private String ip;

    /** 设备端口 */
    private Integer port;

    /** 宿主机 IP */
    private String hostIp;

    /** 实例序号 */
    private Integer instanceIndex;

    /** 模式：BRIDGE / NAT */
    private String mode;

    /** 型号 */
    private String model;

    /** 分组 ID */
    private Long groupId;

    /** 状态：ONLINE / OFFLINE / UNKNOWN / DISABLED */
    private String status;

    /** 版本号 */
    private String version;

    /** 最近在线时间 */
    private LocalDateTime lastSeenAt;

    /** 来源：MANUAL / DISCOVERED */
    private String source;

    /** 备注 */
    private String remark;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private String whoCreated;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime whenCreated;

    /** 修改人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String whoModified;

    /** 修改时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime whenModified;

    /** 逻辑删除标记 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
