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
 * 网段发现任务实体
 */
@Data
@TableName("discover_task")
public class DiscoverTask {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** CIDR 网段，如 192.168.30.0/24 */
    private String cidr;

    /** 起始端口 */
    private Integer portFrom;

    /** 结束端口 */
    private Integer portTo;

    /** 状态：PENDING / RUNNING / DONE / TIMEOUT / FAILED */
    private String status;

    /** 发现的设备数量 */
    private Integer foundCount;

    /** 待扫描 IP 总数 */
    private Integer totalIpCount;

    /** 已扫描 IP 数量 */
    private Integer scannedIpCount;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 任务消息 */
    private String message;

    /** 逐 IP 发现结果 JSON */
    private String detail;

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
