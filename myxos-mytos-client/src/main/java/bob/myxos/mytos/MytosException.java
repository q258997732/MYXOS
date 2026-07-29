package bob.myxos.mytos;

/**
 * MYTOS 设备 API 调用异常
 * 当设备端返回错误码或发生网络异常时抛出
 */
public class MytosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 设备返回的错误码 */
    private final Integer deviceCode;

    public MytosException(String message) {
        super(message);
        this.deviceCode = null;
    }

    public MytosException(Integer deviceCode, String message) {
        super(message);
        this.deviceCode = deviceCode;
    }

    public MytosException(String message, Throwable cause) {
        super(message, cause);
        this.deviceCode = null;
    }

    public Integer getDeviceCode() {
        return deviceCode;
    }
}
