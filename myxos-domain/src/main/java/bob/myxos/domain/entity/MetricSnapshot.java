package bob.myxos.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标快照实体（高容量表）
 */
@Data
@TableName("metric_snapshot")
public class MetricSnapshot {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备 ID */
    private Long deviceId;

    /** 指标类型：CPU / MEM / DISK / NET_RX / NET_TX / TEMP / UPTIME / VERSION / CUSTOM */
    private String metricType;

    /** 指标原始值（字符串形式） */
    private String metricValue;

    /** 指标数值（可数值化时填充） */
    private BigDecimal metricNum;

    /** 扩展信息（JSON 字符串） */
    private String extra;

    /** 采集时间 */
    private LocalDateTime collectedAt;

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
