package bob.myxos.mytos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Adb shell 执行结果响应
 *
 * <p>
 * 设备端 {@code data} 为对象（含 shell_code），命令实际输出放在 {@code message/msg} 字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShellResp extends MytosBaseResp {
    private JsonNode data;
}
