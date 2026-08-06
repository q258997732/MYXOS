package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 阈值规则创建/更新请求 DTO
 */
@Data
public class ThresholdRuleReq {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 64, message = "规则名称长度不能超过 64")
    private String name;

    /** 指标类型 */
    private String metricType;

    /** 指标目录编码；优先于历史 metricType。 */
    private String metricCode;

    /** 条件类型：NUMERIC / STRING / NONE，为空时按 NUMERIC 处理 */
    @Pattern(regexp = "^(NUMERIC|STRING|ENUM|NONE)$", message = "条件类型仅支持 NUMERIC/STRING/ENUM/NONE")
    private String conditionType;

    /** 比较操作：GT / GTE / LT / LTE / EQ / NE / CONTAINS（conditionType=NONE 时可空） */
    @Pattern(regexp = "^(GT|GTE|LT|LTE|EQ|NE|CONTAINS|NOT_CONTAINS|IN|NOT_IN)$", message = "比较操作不合法")
    private String compareOp;

    /** 阈值（conditionType=NUMERIC 时必填，由 Service 层校验） */
    @DecimalMin(value = "0", message = "阈值不能为负数")
    private BigDecimal thresholdValue;

    /** 字符判断目标值（conditionType=STRING 时必填，由 Service 层校验） */
    @Size(max = 255, message = "字符判断目标值长度不能超过 255")
    private String thresholdText;

    /** 枚举阈值选项 JSON 数组。 */
    @Size(max = 2048, message = "枚举阈值选项长度不能超过 2048")
    private String thresholdOptions;

    /** 触发模式：DURATION / CONSECUTIVE */
    @NotBlank(message = "触发模式不能为空")
    @Pattern(regexp = "^(DURATION|CONSECUTIVE)$", message = "触发模式仅支持 DURATION 或 CONSECUTIVE")
    private String triggerMode;

    /** 持续秒数（triggerMode=DURATION 时有效） */
    @Min(value = 0, message = "持续秒数不能为负数")
    private Integer durationSec;

    /** 连续次数（triggerMode=CONSECUTIVE 时有效） */
    @Min(value = 2, message = "连续次数至少为 2")
    private Integer consecutiveCount;

    /** 作用范围类型：ALL / GROUP / DEVICE */
    @NotBlank(message = "作用范围类型不能为空")
    @Pattern(regexp = "^(ALL|GROUP|DEVICE)$", message = "作用范围仅支持 ALL/GROUP/DEVICE")
    private String scopeType;

    /** 作用对象 ID（scopeType=ALL 时为 null） */
    private Long scopeId;

    /** 设备 ID 列表（scopeType=DEVICE 多选时使用，优先于 scopeId） */
    private List<Long> scopeIds;

    /** 安卓实例名（逗号分隔多实例，仅 metricType=ANDROID_STATUS 时有效，为空表示范围内全部实例） */
    @Size(max = 2048, message = "安卓实例名长度不能超过 2048")
    private String scopeAndroidName;

    /** 应用包名（APP_PROCESS_STATE 指标必填） */
    @Pattern(regexp = "^$|^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$", message = "应用包名格式不合法")
    private String scopeAppPackage;

    /** 动作列表 */
    @Valid
    private List<ThresholdActionReq> actions;

    /**
     * 阈值动作请求 DTO
     */
    @Data
    public static class ThresholdActionReq {

        /** 动作类型：LOG / OPERATION */
        @NotBlank(message = "动作类型不能为空")
        @Pattern(regexp = "^(LOG|OPERATION)$", message = "动作类型仅支持 LOG 或 OPERATION")
        private String actionType;

        /** 日志级别（actionType=LOG 时有效） */
        private String logLevel;

        /** 操作类型（actionType=OPERATION 时有效） */
        private String operationCode;

        /** 操作参数（JSON 字符串） */
        private String operationParams;

        /** 同一规则内动作执行顺序 */
        @Min(value = 0, message = "排序值不能为负数")
        private Integer sort;
    }
}
