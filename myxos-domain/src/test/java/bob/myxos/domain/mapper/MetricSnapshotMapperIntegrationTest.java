package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.MetricSnapshot;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证指标快照 Mapper 的实际分组查询。 */
class MetricSnapshotMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        PooledDataSource dataSource = new PooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:metric_snapshot;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS metric_snapshot");
            statement.execute("CREATE TABLE metric_snapshot ("
                    + "id BIGINT PRIMARY KEY, device_id BIGINT, metric_type VARCHAR(64), metric_code VARCHAR(64), "
                    + "target_type VARCHAR(32), android_name VARCHAR(128), metric_value VARCHAR(255), "
                    + "metric_num DECIMAL(20,4), extra VARCHAR(255), collected_at TIMESTAMP, deleted TINYINT)");
            statement.execute("INSERT INTO metric_snapshot VALUES "
                    + "(1, 1, 'STATUS', 'ANDROID_STATUS', 'ANDROID_INSTANCE', '实例一', 'STOPPED', NULL, NULL, TIMESTAMP '2026-08-06 10:00:00', 0),"
                    + "(2, 1, 'STATUS', 'ANDROID_STATUS', 'ANDROID_INSTANCE', '实例一', 'RUNNING', NULL, NULL, TIMESTAMP '2026-08-06 10:01:00', 0),"
                    + "(3, 1, 'STATUS', 'ANDROID_STATUS', 'ANDROID_INSTANCE', '实例二', 'STOPPED', NULL, NULL, TIMESTAMP '2026-08-06 10:00:30', 0),"
                    + "(4, 1, 'CPU', 'CPU_USAGE_PERCENT', 'ANDROID_INSTANCE', '实例一', '80', 80, NULL, TIMESTAMP '2026-08-06 10:02:00', 0)");
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MetricSnapshotMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void 应按指标目标与安卓实例分别返回最新快照() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<MetricSnapshot> snapshots = session.getMapper(MetricSnapshotMapper.class)
                    .selectLatestPerMetricTargetByDevice(1L);

            assertEquals(3, snapshots.size());
            assertEquals(1, snapshots.stream().filter(snapshot -> "ANDROID_STATUS".equals(snapshot.getMetricCode())
                    && "实例一".equals(snapshot.getAndroidName()) && "RUNNING".equals(snapshot.getMetricValue())).count());
            assertEquals(1, snapshots.stream().filter(snapshot -> "ANDROID_STATUS".equals(snapshot.getMetricCode())
                    && "实例二".equals(snapshot.getAndroidName()) && "STOPPED".equals(snapshot.getMetricValue())).count());
        }
    }
}
