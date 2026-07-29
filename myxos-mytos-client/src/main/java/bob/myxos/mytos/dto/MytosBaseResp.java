package bob.myxos.mytos.dto;

import lombok.Data;

/**
 * MYTOS 设备 API 基础响应
 * 所有设备端接口返回的公共字段
 */
@Data
public class MytosBaseResp {
    /** 状态码：200 成功，201 通用错误，202 操作失败 */
    private Integer code;
    /** 成功消息或版本号等 */
    private String msg;
    /** 通用错误信息（code=201 时返回） */
    private String error;
    /** 操作失败原因（code=202 时返回） */
    private String reason;
}
