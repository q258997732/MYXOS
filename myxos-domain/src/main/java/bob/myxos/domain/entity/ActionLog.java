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
 * 动作与系统日志实体
 */
@Data
@TableName("action_log")
public class ActionLog {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务 ID（可空） */
    private Long taskId;

    /** 关联告警 ID（可空） */
    private Long alarmId;

    /** 设备 ID */
    private Long deviceId;

    /** 动作类型：LOG / OPERATION / SYSTEM */
    private String actionType;

    /** 日志级别：DEBUG / INFO / WARN / ERROR */
    private String logLevel;

    /** 日志内容 */
    private String message;

    /** 日志产生时间 */
    private LocalDateTime createdAt;

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
