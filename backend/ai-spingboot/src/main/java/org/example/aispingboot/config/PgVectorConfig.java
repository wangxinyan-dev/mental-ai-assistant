package org.example.aispingboot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * PgVector 向量数据源配置
 *
 * MySQL 由 Spring Boot 自动配置（spring.datasource.*），作为默认主数据源。
 * PgVector 在此独立定义，仅用于 RAG 向量存储与检索，不影响 MyBatis-Plus。
 */
@Configuration
public class PgVectorConfig {

    @Value("${rag.vector-store.pg.url:jdbc:postgresql://localhost:5432/rag_vector}")
    private String pgUrl;

    @Value("${rag.vector-store.pg.username:postgres}")
    private String pgUsername;

    @Value("${rag.vector-store.pg.password:change-me-in-production}")
    private String pgPassword;

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("PgVectorHikariPool");
        ds.setJdbcUrl(pgUrl);
        ds.setUsername(pgUsername);
        ds.setPassword(pgPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        ds.setConnectionTestQuery("SELECT 1");
        ds.setLeakDetectionThreshold(60000);
        return ds;
    }

    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * PgVector 事务管理器：用于影子表原子切换（RENAME 切换需在单事务内保证 DDL 原子性）。
     * 独立于主数据源（MySQL）的默认事务管理器，避免 @Transactional 事务管理器歧义。
     */
    @Bean(name = "pgVectorTransactionManager")
    public PlatformTransactionManager pgVectorTransactionManager(
            @Qualifier("pgVectorDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
