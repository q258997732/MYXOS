package bob.myxos.collector.cleanup;

import bob.myxos.domain.entity.SysConfig;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import bob.myxos.domain.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据清理定时任务
 * <p>
 * 按配置的保留周期清理 metric_snapshot、action_log、alarm_event 过期数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupJob {

    private final SysConfigMapper sysConfigMapper;
    private final MetricSnapshotMapper metricSnapshotMapper;
    private final ActionLogMapper actionLogMapper;
    private final AlarmEventMapper alarmEventMapper;

    /**
     * 按配置 cron 表达式执行清理，默认每天凌晨 3 点
     */
    @Scheduled(cron = "${myxos.cleanup.cron:0 0 3 * * ?}")
    public void cleanup() {
        log.info("开始执行数据清理任务");
        int metricDays = Integer.parseInt(getConfig("metric.retention.days", "7"));
        int logDays = Integer.parseInt(getConfig("log.retention.days", "30"));
        int alarmDays = Integer.parseInt(getConfig("alarm.retention.days", "90"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime metricDeadline = now.minusDays(metricDays);
        LocalDateTime logDeadline = now.minusDays(logDays);
        LocalDateTime alarmDeadline = now.minusDays(alarmDays);

        int metricTotal = deleteInBatches(metricSnapshotMapper, metricDeadline);
        int logTotal = deleteInBatches(actionLogMapper, logDeadline);
        int alarmTotal = deleteInBatches(alarmEventMapper, alarmDeadline);

        log.info("数据清理完成：metric_snapshot={}, action_log={}, alarm_event={}",
                metricTotal, logTotal, alarmTotal);
    }

    /**
     * 循环批量删除，直到没有记录被删除
     */
    private int deleteInBatches(MetricSnapshotMapper mapper, LocalDateTime deadline) {
        int total = 0;
        int deleted;
        do {
            deleted = mapper.deleteByCollectedAtBefore(deadline);
            total += deleted;
        } while (deleted > 0);
        return total;
    }

    private int deleteInBatches(ActionLogMapper mapper, LocalDateTime deadline) {
        int total = 0;
        int deleted;
        do {
            deleted = mapper.deleteByCreatedAtBefore(deadline);
            total += deleted;
        } while (deleted > 0);
        return total;
    }

    private int deleteInBatches(AlarmEventMapper mapper, LocalDateTime deadline) {
        int total = 0;
        int deleted;
        do {
            deleted = mapper.deleteByFiredAtBefore(deadline);
            total += deleted;
        } while (deleted > 0);
        return total;
    }

    private String getConfig(String key, String defaultValue) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key)
                        .eq(SysConfig::getDeleted, 0));
        return config == null ? defaultValue : config.getConfigValue();
    }
}
