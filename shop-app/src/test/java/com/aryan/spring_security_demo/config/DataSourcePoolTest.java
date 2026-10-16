package com.aryan.spring_security_demo.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The connection pool runs with the production settings in application.yml, as
 * the pool itself sees them: a misspelt key there is ignored silently, and the
 * pool would fall back to HikariCP's defaults (a 30-second wait for a connection,
 * a shrinking pool, no keepalive).
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class DataSourcePoolTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("the pool is fixed-size, fails fast, keeps connections alive and caches statements")
    void thePoolHasTheProductionSettings() {
        HikariDataSource pool = (HikariDataSource) dataSource;

        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getMinimumIdle()).as("fixed-size: as many idle as the maximum").isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).as("ms to wait for a connection before a 503").isEqualTo(5_000);
        assertThat(pool.getKeepaliveTime()).isEqualTo(120_000);
        assertThat(pool.getMaxLifetime()).isEqualTo(1_800_000);
        assertThat(pool.getLeakDetectionThreshold()).isEqualTo(20_000);
        assertThat(pool.getDataSourceProperties())
                .containsEntry("cachePrepStmts", "true")
                .containsEntry("prepStmtCacheSize", "250")
                .doesNotContainKey("useServerPrepStmts")
                .containsEntry("useLocalSessionState", "true");
    }
}
