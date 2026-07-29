package bob.myxos.common.enums;

/**
 * 设备操作码枚举
 * 对应 MYTOS 设备端 HTTP API 支持的操作
 */
public enum OperationCode {
    /** 重启设备 */
    REBOOT,
    /** 打开 ADB */
    ADB_ON,
    /** 关闭 ADB */
    ADB_OFF,
    /** 打开保活 */
    KEEPALIVE_ON,
    /** 关闭保活 */
    KEEPALIVE_OFF,
    /** 设置剪贴板 */
    SET_CLIPBOARD,
    /** 清除代理 */
    CLEAR_PROXY,
    /** 设置代理 */
    SET_PROXY,
    /** 上传文件 */
    UPLOAD_FILE,
    /** 刷新定位 */
    REFRESH_LOC,
    /** 设置指纹 */
    SET_FINGERPRINT,
    /** 设置语言 */
    SET_LANGUAGE,
    /** 设置代理过滤 */
    SET_PROXY_FILTER
}
