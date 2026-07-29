package bob.myxos.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 采集服务配置属性
 * 对应 application.yml 中 myxos.collector 前缀的配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "myxos.collector")
public class CollectorProperties {

    /** 采集任务调度间隔（毫秒） */
    private Long intervalMs = 30000L;

    /** 指标采集线程池核心线程数 */
    private Integer metricPoolCore = 8;

    /** 指标采集线程池最大线程数 */
    private Integer metricPoolMax = 32;

    /** 指标采集线程池队列容量 */
    private Integer metricPoolQueue = 500;

    /** 操作任务线程池核心线程数 */
    private Integer opPoolCore = 4;

    /** 操作任务线程池最大线程数 */
    private Integer opPoolMax = 16;

    /** 操作任务线程池队列容量 */
    private Integer opPoolQueue = 200;
}
