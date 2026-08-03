package bob.myxos.common.enums;

/**
 * 指标类型枚举
 */
public enum MetricType {
    /** CPU 使用率 */
    CPU,
    /** 内存使用率 */
    MEM,
    /** 磁盘使用率 */
    DISK,
    /** 网络接收速率 */
    NET_RX,
    /** 网络发送速率 */
    NET_TX,
    /** 温度 */
    TEMP,
    /** 运行时长 */
    UPTIME,
    /** 版本号 */
    VERSION,
    /** 安卓实例状态 */
    ANDROID_STATUS,
    /** 自定义指标 */
    CUSTOM
}
