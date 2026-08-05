package bob.myxos.common.enums;

/**
 * 阈值触发条件类型枚举
 */
public enum ConditionType {
    /** 数值判断（compareOp + thresholdValue） */
    NUMERIC,
    /** 字符判断（compareOp + thresholdText，支持 EQ/NE/CONTAINS） */
    STRING,
    /** 无需条件（状态类指标，检测到对应状态即触发） */
    NONE,
    /** 枚举值判断（compareOp + thresholdText，支持 IN / NOT_IN） */
    ENUM
}
