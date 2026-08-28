package org.example.aispingboot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * MySQL 主数据源配置
 *
 * 使用 DataSourceProperties 作为中间层绑定 spring.datasource.*，
 * 然后构建 HikariDataSource 并标记为 @Primary，
 * 确保 MyBatis-Plus 的 Mapper 默认路由到 MySQL。
 *
 * 同时显式声明 mysqlJdbcTemplate bean：项目存在双数据源（MySQL + PG），
 * 若不显式声明，@Resource JdbcTemplate 会因字段名歧义误注入 PG 的模板。
 * 显式命名 + @Qualifier 彻底消除路由歧义——与其他数据源（pgVectorJdbcTemplate）
 * 各自一条明确访问路径的模式一致。
 */
@Configuration
public class PrimaryDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource mysqlDataSource(DataSourceProperties mysqlDataSourceProperties) {
        HikariDataSource ds = mysqlDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("MentalHealthHikariPool");
        return ds;
    }

    /** MySQL 主库专用 JdbcTemplate——显式命名，供需走主库的 DDL/查询（如审计分区维护）绑定 */
    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("mysqlDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
