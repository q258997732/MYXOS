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
 * 操作任务实体（手动 / 自动）
 */
@Data
@TableName("op_task")
public class OpTask {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备 ID */
    private Long deviceId;

    /** 操作类型 */
    private String operationCode;

    /** 操作参数（JSON 字符串） */
    private String params;

    /** 来源：MANUAL / AUTO */
    private String source;

    /** 来源引用 ID（如告警 ID） */
    private Long sourceRefId;

    /** 状态：PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 计划执行时间 */
    private LocalDateTime scheduledAt;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 执行结果消息 */
    private String resultMsg;

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
