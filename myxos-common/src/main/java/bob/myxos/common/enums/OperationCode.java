package bob.myxos.common.enums;

/**
 * 设备操作码枚举
 * 对应 MYTOS 设备端 HTTP API 支持的操作
 * <p>
 * 范围以 docs/superpowers/plans/2026-08-03-mytos-api-mapping.md 最终确认的首期接口为准。
 */
public enum OperationCode {
    /** 重启主机 */
    REBOOT_HOST,

    // ==================== 容器实例生命周期 ====================

    /** 运行安卓容器 */
    RUN_ANDROID,
    /** 停止安卓容器 */
    STOP_ANDROID,
    /** 重启安卓容器 */
    REBOOT_ANDROID,
    /** 重置安卓容器 */
    RESET_ANDROID,
    /** 重命名安卓容器 */
    RENAME_ANDROID,

    // ==================== 安卓实例内部操作 ====================

    /** 设置剪贴板 */
    SET_CLIPBOARD,
    /** 获取剪贴板 */
    GET_CLIPBOARD,
    /** 设置系统语言 */
    SET_LANGUAGE,
    /** IP 智能定位 */
    REFRESH_LOCATION,
    /** 设备截图（临时查看） */
    SCREENSHOT,
    /** 执行 Adb 命令 */
    SHELL_ADB
}
