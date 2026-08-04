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
    /** 设备在线状态（1 在线 / 0 离线） */
    ONLINE,
    /** 设备离线状态（1 离线 / 0 在线） */
    OFFLINE,
    /** 安卓实例状态（字符串：RUNNING/STOPPED/TRANSITION/UNKNOWN） */
    ANDROID_STATUS,
    /** 运行中的安卓实例数量 */
    ANDROID_ONLINE,
    /** 非运行（已停止/过渡/未知）的安卓实例数量 */
    ANDROID_OFFLINE,
    /** 自定义指标 */
    CUSTOM
}
