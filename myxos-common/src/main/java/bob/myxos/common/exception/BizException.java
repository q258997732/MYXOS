package bob.myxos.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 携带业务状态码，由全局异常处理器统一转换为 Result 响应
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务状态码 */
    private final Integer code;

    /**
     * 构造默认 500 状态码的业务异常
     *
     * @param message 错误信息
     */
    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 构造指定状态码的业务异常
     *
     * @param code    业务状态码
     * @param message 错误信息
     */
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
