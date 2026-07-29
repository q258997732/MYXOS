package bob.myxos.collector.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 * 提供指标采集与操作任务两个独立的线程池
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class ThreadPoolConfig {

    private final CollectorProperties properties;

    /**
     * 指标采集线程池
     * 用于并发执行各设备的指标采集任务
     *
     * @return 指标采集线程池
     */
    @Bean(name = "metricCollectExecutor")
    public ThreadPoolTaskExecutor metricCollectExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getMetricPoolCore());
        executor.setMaxPoolSize(properties.getMetricPoolMax());
        executor.setQueueCapacity(properties.getMetricPoolQueue());
        executor.setThreadNamePrefix("metric-collect-");
        // 队列满时由调用线程执行，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 操作任务线程池
     * 用于执行阈值动作、运维操作等异步任务
     *
     * @return 操作任务线程池
     */
    @Bean(name = "opTaskExecutor")
    public ThreadPoolTaskExecutor opTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getOpPoolCore());
        executor.setMaxPoolSize(properties.getOpPoolMax());
        executor.setQueueCapacity(properties.getOpPoolQueue());
        executor.setThreadNamePrefix("op-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
