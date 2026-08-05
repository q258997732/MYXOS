package bob.myxos.collector.config;

import bob.myxos.collector.collector.DeviceStatusRefreshJob;
import bob.myxos.domain.entity.SysConfig;
import bob.myxos.domain.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.Date;

/**
 * 指标采集动态调度配置
 * <p>
 * 以 Trigger 方式调度 {@link MetricCollectJob#collect()}：每轮执行完成后，
 * 重新读取 sys_config 的 collect.interval.sec（秒）计算下一轮执行时间。
 * 相比 {@code @Scheduled(fixedDelayString)} 的启动期固定间隔，
 * 页面上修改采集间隔后下一轮调度即生效，无需重启采集服务。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MetricCollectScheduleConfig implements SchedulingConfigurer {

    /** 采集间隔配置键（秒） */
    private static final String INTERVAL_KEY = "collect.interval.sec";
    /** 默认采集间隔（秒），配置缺失或非法时使用 */
    private static final long DEFAULT_INTERVAL_SEC = 30L;
    /** 采集间隔下限（秒），防止配置过小压垮设备 */
    private static final long MIN_INTERVAL_SEC = 5L;

    private final DeviceStatusRefreshJob deviceStatusRefreshJob;
    private final SysConfigMapper sysConfigMapper;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(deviceStatusRefreshJob::refresh, triggerContext -> {
            Date lastCompletion = triggerContext.lastCompletionTime();
            // 首次调度（lastCompletion 为 null）立即执行，保持与原 fixedDelay 启动即跑一致
            long base = lastCompletion == null
                    ? System.currentTimeMillis() - currentIntervalMs()
                    : lastCompletion.getTime();
            return new Date(base + currentIntervalMs());
        });
    }

    /**
     * 读取当前采集间隔（毫秒）：每次调度重新查询数据库，使配置修改即时生效
     *
     * @return 采集间隔毫秒数，不小于 {@link #MIN_INTERVAL_SEC} 秒
     */
    private long currentIntervalMs() {
        long intervalSec = DEFAULT_INTERVAL_SEC;
        try {
            SysConfig config = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, INTERVAL_KEY)
                            .eq(SysConfig::getDeleted, 0));
            if (config != null && config.getConfigValue() != null) {
                intervalSec = Long.parseLong(config.getConfigValue().trim());
            }
        } catch (Exception e) {
            log.warn("读取采集间隔配置失败，使用默认值 {} 秒：{}", DEFAULT_INTERVAL_SEC, e.getMessage());
        }
        if (intervalSec < MIN_INTERVAL_SEC) {
            intervalSec = MIN_INTERVAL_SEC;
        }
        return intervalSec * 1000L;
    }
}
