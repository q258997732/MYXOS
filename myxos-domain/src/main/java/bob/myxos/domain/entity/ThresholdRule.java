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
 * 阈值规则实体
 */
@Data
@TableName("threshold_rule")
public class ThresholdRule {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String name;

    /** 指标类型 */
    private String metricType;

    /** 指标编码 */
    private String metricCode;

    /** 条件类型：NUMERIC（数值判断） / STRING（字符判断） / NONE（状态触发，无需条件） */
    private String conditionType;

    /** 比较操作：GT / GTE / LT / LTE / EQ / NE / CONTAINS */
    private String compareOp;

    /** 阈值（conditionType=NUMERIC 时有效） */
    private BigDecimal thresholdValue;

    /** 字符判断目标值（conditionType=STRING 时有效） */
    private String thresholdText;

    /** 触发模式：DURATION（持续时长） / CONSECUTIVE（连续次数） */
    private String triggerMode;

    /** 持续秒数（triggerMode=DURATION 时有效，0 表示即时触发） */
    private Integer durationSec;

    /** 连续次数（triggerMode=CONSECUTIVE 时有效，>=2） */
    private Integer consecutiveCount;

    /** 作用范围类型：ALL / GROUP / DEVICE */
    private String scopeType;

    /** 作用对象 ID（分组 ID 或设备 ID，scopeType=ALL 时为 null） */
    private Long scopeId;

    /** 设备 ID 列表（逗号分隔，scopeType=DEVICE 且多选时优先于 scopeId） */
    private String scopeIds;

    /** 安卓实例名（仅 ANDROID_STATUS 指标生效，为空表示范围内全部实例） */
    private String scopeAndroidName;

    /** 是否启用：1 启用，0 禁用 */
    private Integer enabled;

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
