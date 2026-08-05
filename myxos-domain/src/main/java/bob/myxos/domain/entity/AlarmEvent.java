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
 * 告警事件实体
 */
@Data
@TableName("alarm_event")
public class AlarmEvent {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发规则 ID */
    private Long ruleId;

    /** 设备 ID */
    private Long deviceId;

    /** 安卓实例名（ANDROID_STATUS 触发时记录，按实例维度去重告警） */
    private String androidName;

    /** 指标类型 */
    private String metricType;

    /** 触发时的指标值 */
    private String metricValue;

    /** 触发时的阈值 */
    private String thresholdValue;

    /** 触发时间 */
    private LocalDateTime firedAt;

    /** 恢复时间 */
    private LocalDateTime resolvedAt;

    /** 状态：FIRING / RESOLVED */
    private String status;

    /** 触发规则名称（非持久化，查询时填充） */
    @TableField(exist = false)
    private String ruleName;

    /** 设备名称（非持久化，查询时填充，无名称时回退为 IP） */
    @TableField(exist = false)
    private String deviceName;

    /** 告警级别（非持久化，取该告警首条 LOG 动作的日志级别，无 LOG 动作时为空） */
    @TableField(exist = false)
    private String level;

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
