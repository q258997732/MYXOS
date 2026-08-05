package bob.myxos.common.enums;

/**
 * 阈值比较操作符枚举
 */
public enum CompareOp {
    /** 大于 */
    GT,
    /** 大于等于 */
    GTE,
    /** 小于 */
    LT,
    /** 小于等于 */
    LTE,
    /** 等于 */
    EQ,
    /** 不等于 */
    NE,
    /** 包含（仅用于字符判断） */
    CONTAINS,
    /** 不包含（仅用于字符串判断） */
    NOT_CONTAINS,
    /** 属于集合 */
    IN,
    /** 不属于集合 */
    NOT_IN
}
