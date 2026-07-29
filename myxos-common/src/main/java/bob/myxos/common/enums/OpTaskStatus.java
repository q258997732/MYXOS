package bob.myxos.common.enums;

/**
 * 操作任务状态枚举
 */
public enum OpTaskStatus {
    /** 待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 执行成功 */
    SUCCESS,
    /** 执行失败 */
    FAILED,
    /** 执行超时 */
    TIMEOUT
}
