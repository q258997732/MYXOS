package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备信息响应（/info 接口）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InfoResp extends MytosBaseResp {
    /** 设备信息数据 */
    private InfoData data;

    /**
     * 设备信息数据
     */
    @Data
    public static class InfoData {
        /** 宿主机 IP */
        private String hostIp;
        /** 实例编号 */
        private String instance;
        /** 设备名称 */
        private String name;
        /** 镜像构建时间戳 */
        private String buildTime;
    }
}
