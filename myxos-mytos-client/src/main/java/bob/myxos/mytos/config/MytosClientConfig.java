package bob.myxos.mytos.config;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MYTOS 客户端 OkHttp 配置
 * 提供连接池与超时参数合理的 OkHttpClient Bean
 */
@Configuration
public class MytosClientConfig {

    /** 连接超时时间（秒） */
    private static final int CONNECT_TIMEOUT_SEC = 3;
    /** 读取超时时间（秒） */
    private static final int READ_TIMEOUT_SEC = 10;
    /** 写入超时时间（秒） */
    private static final int WRITE_TIMEOUT_SEC = 10;
    /** 最大空闲连接数 */
    private static final int MAX_IDLE_CONNECTIONS = 32;
    /** 空闲连接存活时间（分钟） */
    private static final int KEEP_ALIVE_DURATION_MIN = 5;

    /**
     * 构建用于访问 MYTOS 设备的 OkHttpClient
     *
     * @return 配置好的 OkHttpClient 实例
     */
    @Bean
    public OkHttpClient mytosOkHttpClient() {
        Dispatcher dispatcher = new Dispatcher();
        // 提高并发能力，适应批量设备采集场景
        dispatcher.setMaxRequests(128);
        dispatcher.setMaxRequestsPerHost(32);

        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_DURATION_MIN, TimeUnit.MINUTES))
                .dispatcher(dispatcher)
                .retryOnConnectionFailure(true)
                .build();
    }
}
