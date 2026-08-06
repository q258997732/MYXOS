package bob.myxos.collector.cleanup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataCleanupJobTest {

    @Test
    void 指标保留期应在非法值时回退30天() {
        assertEquals(30, DataCleanupJob.parseMetricRetentionDays("not-a-number"));
        assertEquals(30, DataCleanupJob.parseMetricRetentionDays("0"));
        assertEquals(30, DataCleanupJob.parseMetricRetentionDays("3651"));
    }
}
