package org.rdlinux.flyway.gaussdb.autoconfigure;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.rdlinux.flyway.gaussdb.GaussDBDatabaseType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * 为 Spring Boot 的 Flyway 自动配置解析 GaussDB 的迁移目录占位符。
 */
@AutoConfiguration
@ConditionalOnClass({FlywayConfigurationCustomizer.class, GaussDBDatabaseType.class})
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GaussDBFlywayAutoConfiguration {

    /**
     * 注册 GaussDB Flyway 迁移目录定制器。
     *
     * @return Flyway 配置定制器
     */
    @Bean
    public FlywayConfigurationCustomizer gaussDBFlywayLocationCustomizer() {
        return this::resolveGaussDBVendorLocation;
    }

    /**
     * 将未解析的 {@code {vendor}} 目录替换为 GaussDB 目录。
     *
     * @param configuration Spring Boot 已配置的数据源和迁移目录
     */
    private void resolveGaussDBVendorLocation(FluentConfiguration configuration) {
        Location[] locations = configuration.getLocations();
        boolean hasVendorPlaceholder = Arrays.stream(locations)
                .map(Location::getDescriptor)
                .anyMatch(location -> location.contains("{vendor}"));
        if (!hasVendorPlaceholder) {
            return;
        }

        DataSource dataSource = configuration.getDataSource();
        if (dataSource == null) {
            return;
        }

        String jdbcUrl;
        try (Connection connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取 Flyway 数据源的 JDBC URL", exception);
        }

        if (!new GaussDBDatabaseType().handlesJDBCUrl(jdbcUrl)) {
            return;
        }

        Location[] resolvedLocations = Arrays.stream(locations)
                .map(location -> new Location(location.getDescriptor().replace("{vendor}", "gaussdb")))
                .toArray(Location[]::new);
        configuration.locations(resolvedLocations);
    }
}
