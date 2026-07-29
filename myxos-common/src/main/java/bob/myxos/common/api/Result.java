package bob.myxos.common.api;

import lombok.Data;

/**
 * 统一响应封装
 *
 * @param <T> 数据载荷类型
 */
@Data
public class Result<T> {

    /** 业务状态码：200 成功，其他为失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 数据载荷 */
    private T data;

    /**
     * 构造成功响应
     *
     * @param data 数据载荷
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("ok");
        r.setData(data);
        return r;
    }

    /**
     * 构造无数据的成功响应
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应（默认 500）
     *
     * @param msg 错误信息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<>();
        r.setCode(500);
        r.setMsg(msg);
        return r;
    }

    /**
     * 构造失败响应（自定义状态码）
     *
     * @param code 状态码
     * @param msg  错误信息
     * @param <T>  数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
