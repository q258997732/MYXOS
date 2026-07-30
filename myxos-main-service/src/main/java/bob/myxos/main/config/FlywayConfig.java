package bob.myxos.main.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 配置
 * <p>
 * 在迁移前先执行 repair，用于处理本地开发环境中因手动建表或历史失败迁移导致的
 * schema history 不一致问题。生产环境建议通过 flyway 命令行单独管理 repair。
 */
@Configuration
public class FlywayConfig {

    /**
     * 自定义迁移策略：先 repair 再 migrate
     *
     * @return repair 后迁移的策略
     */
    @Bean
    public FlywayMigrationStrategy cleanRepairStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
