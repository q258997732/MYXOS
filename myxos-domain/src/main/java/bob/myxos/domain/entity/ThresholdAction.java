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
 * 阈值触发的动作实体
 */
@Data
@TableName("threshold_action")
public class ThresholdAction {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属规则 ID */
    private Long ruleId;

    /** 动作类型：LOG / OPERATION */
    private String actionType;

    /** 日志级别（actionType=LOG 时有效）：DEBUG / INFO / WARN / ERROR */
    private String logLevel;

    /** 操作类型（actionType=OPERATION 时有效） */
    private String operationCode;

    /** 操作参数（JSON 字符串） */
    private String operationParams;

    /** 同一规则内动作执行顺序 */
    private Integer sort;

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
