package bob.myxos.mytos;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * MYTOS 客户端工厂
 * 根据设备 IP 与端口创建 {@link MytosClient} 实例
 */
@Component
@RequiredArgsConstructor
public class MytosClientFactory {

    @Qualifier("mytosOkHttpClient")
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 创建指定设备的 MYTOS 客户端
     *
     * @param ip   设备 IP 地址
     * @param port 设备 API 端口
     * @return 绑定该设备的 MytosClient 实例
     */
    public MytosClient create(String ip, int port) {
        if (ip == null || ip.trim().isEmpty()) {
            throw new IllegalArgumentException("设备 IP 不能为空");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("设备端口非法: " + port);
        }
        return new MytosClient(httpClient, objectMapper, "http://" + ip + ":" + port);
    }
}
