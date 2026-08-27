package org.example.aispingboot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * MySQL 主数据源配置
 *
 * 使用 DataSourceProperties 作为中间层绑定 spring.datasource.*，
 * 然后构建 HikariDataSource 并标记为 @Primary，
 * 确保 MyBatis-Plus 的 Mapper 默认路由到 MySQL。
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
}
