package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 版本查询响应（/queryversion 接口）
 * 版本号通过 msg 字段返回
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VersionResp extends MytosBaseResp {
    // 版本号通过父类的 msg 字段返回，无需额外字段
}
