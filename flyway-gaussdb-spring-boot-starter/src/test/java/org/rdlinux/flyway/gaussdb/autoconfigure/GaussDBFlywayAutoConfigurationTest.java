package org.rdlinux.flyway.gaussdb.autoconfigure;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.core.env.MapPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 GaussDB Flyway 自动配置与 Spring Boot 迁移目录解析的集成。
 */
class GaussDBFlywayAutoConfigurationTest {

    /**
     * 验证自动配置候选清单注册了 GaussDB Flyway 自动配置。
     */
    @Test
    void should_register_auto_configuration_candidate() {
        boolean registered = false;
        for (String candidate : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())) {
            if (GaussDBFlywayAutoConfiguration.class.getName().equals(candidate)) {
                registered = true;
                break;
            }
        }

        assertTrue(registered, "Spring Boot 自动配置清单应包含 GaussDBFlywayAutoConfiguration");
    }

    /**
     * 验证 GaussDB 的 vendor 占位符通过 Spring Boot Flyway 自动配置解析为 GaussDB 目录。
     *
     * @throws SQLException 模拟 JDBC 元数据时抛出
     */
    @Test
    void should_resolve_gaussdb_vendor_location() throws SQLException {
        DataSource dataSource = mockDataSource("jdbc:gaussdb://localhost:8000/ez_auth");

        try (AnnotationConfigApplicationContext context = createEnabledContext(dataSource,
                "classpath:db/migration/{vendor}")) {
            Flyway flyway = context.getBean(Flyway.class);
            assertEquals("classpath:db/migration/gaussdb", getLocationDescriptors(flyway)[0]);
        }
    }

    /**
     * 验证非 GaussDB 的 vendor 占位符仍按 Spring Boot 自身规则解析。
     *
     * @throws SQLException 模拟 JDBC 元数据时抛出
     */
    @Test
    void should_preserve_spring_boot_vendor_resolution_for_mysql() throws SQLException {
        DataSource dataSource = mockDataSource("jdbc:mysql://localhost:3306/ez_auth");

        try (AnnotationConfigApplicationContext context = createEnabledContext(dataSource,
                "classpath:db/migration/{vendor}")) {
            Flyway flyway = context.getBean(Flyway.class);
            assertEquals("classpath:db/migration/mysql", getLocationDescriptors(flyway)[0]);
        }
    }

    /**
     * 验证显式指定目录时不查询数据源连接元数据。
     *
     * @throws SQLException 验证 mock 调用时抛出
     */
    @Test
    void should_leave_explicit_location_unchanged_without_connecting() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        String explicitLocation = "classpath:db/migration/custom";

        try (AnnotationConfigApplicationContext context = createEnabledContext(dataSource, explicitLocation)) {
            Flyway flyway = context.getBean(Flyway.class);
            assertEquals(explicitLocation, getLocationDescriptors(flyway)[0]);
        }

        verify(dataSource, never()).getConnection();
    }

    /**
     * 验证 GaussDB 只替换混合目录中的 vendor 占位符并保留显式目录。
     *
     * @throws SQLException 模拟 JDBC 元数据时抛出
     */
    @Test
    void should_resolve_gaussdb_vendor_location_and_preserve_explicit_location() throws SQLException {
        DataSource dataSource = mockDataSource("jdbc:gaussdb://localhost:8000/ez_auth");

        try (AnnotationConfigApplicationContext context = createEnabledContext(dataSource,
                "classpath:db/migration/{vendor}", "classpath:db/migration/shared")) {
            Flyway flyway = context.getBean(Flyway.class);
            assertEquals(Arrays.asList("classpath:db/migration/gaussdb", "classpath:db/migration/shared"),
                    Arrays.asList(getLocationDescriptors(flyway)));
        }
    }

    /**
     * 验证使用 Flyway 专用数据源时按该数据源的 JDBC URL 解析占位符。
     *
     * @throws SQLException 模拟 JDBC 元数据时抛出
     */
    @Test
    void should_resolve_vendor_location_from_flyway_data_source() throws SQLException {
        DataSource primaryDataSource = mock(DataSource.class);
        DataSource flywayDataSource = mockDataSource("jdbc:gaussdb://localhost:8000/ez_auth");

        try (AnnotationConfigApplicationContext context = createContext(primaryDataSource, "true", flywayDataSource,
                "classpath:db/migration/{vendor}")) {
            Flyway flyway = context.getBean(Flyway.class);
            assertEquals("classpath:db/migration/gaussdb", getLocationDescriptors(flyway)[0]);
            assertSame(flywayDataSource, flyway.getConfiguration().getDataSource());
        }

        verify(primaryDataSource, never()).getConnection();
    }

    /**
     * 验证关闭 Spring Boot Flyway 时不注册 Flyway Bean。
     */
    @Test
    void should_back_off_when_spring_boot_flyway_is_disabled() {
        DataSource dataSource = mock(DataSource.class);

        try (AnnotationConfigApplicationContext context = createContext(dataSource, "false",
                "classpath:db/migration/custom")) {
            assertFalse(context.containsBean("flyway"));
            assertFalse(context.containsBean("gaussDBFlywayLocationCustomizer"));
        }
    }

    /**
     * 创建包含 Spring Boot Flyway 自动配置与 GaussDB 自动配置的测试上下文。
     *
     * @param dataSource 测试用 Flyway 数据源
     * @param locations Flyway 迁移目录
     * @return 已刷新的 Spring 上下文
     */
    private AnnotationConfigApplicationContext createEnabledContext(DataSource dataSource, String... locations) {
        return this.createContext(dataSource, "true", locations);
    }

    /**
     * 创建包含 Spring Boot Flyway 自动配置与 GaussDB 自动配置的测试上下文。
     *
     * @param dataSource 测试用 Flyway 数据源
     * @param enabled Spring Boot Flyway 启用状态
     * @param locations Flyway 迁移目录
     * @return 已刷新的 Spring 上下文
     */
    private AnnotationConfigApplicationContext createContext(DataSource dataSource, String enabled,
                                                               String... locations) {
        return this.createContext(dataSource, enabled, null, locations);
    }

    /**
     * 创建包含 Spring Boot Flyway 自动配置与 GaussDB 自动配置的测试上下文。
     *
     * @param dataSource 主应用数据源
     * @param enabled Spring Boot Flyway 启用状态
     * @param flywayDataSource Flyway 专用数据源，未指定时使用主数据源
     * @param locations Flyway 迁移目录
     * @return 已刷新的 Spring 上下文
     */
    private AnnotationConfigApplicationContext createContext(DataSource dataSource, String enabled,
                                                               DataSource flywayDataSource, String... locations) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.flyway.enabled", enabled);
        properties.put("spring.flyway.locations", locations);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("testProperties", properties));
        context.registerBean("dataSource", DataSource.class, () -> dataSource);
        if (flywayDataSource != null) {
            this.registerFlywayDataSource(context, flywayDataSource);
        }
        context.registerBean("flywayMigrationStrategy", FlywayMigrationStrategy.class, () -> flyway -> {
            // 测试只验证 Flyway 配置，不执行数据库迁移。
        });
        context.register(FlywayAutoConfiguration.class, GaussDBFlywayAutoConfiguration.class);
        context.refresh();
        return context;
    }

    /**
     * 注册 Flyway 专用数据源以验证占位符从 Flyway 实际使用的数据源解析。
     *
     * @param context 测试 Spring 上下文
     * @param dataSource Flyway 专用数据源
     */
    private void registerFlywayDataSource(AnnotationConfigApplicationContext context, DataSource dataSource) {
        context.registerBean("flywayDataSource", DataSource.class, () -> dataSource,
                beanDefinition -> ((AbstractBeanDefinition) beanDefinition)
                        .addQualifier(new AutowireCandidateQualifier(FlywayDataSource.class)));
    }

    /**
     * 创建提供指定 JDBC URL 的数据源 mock。
     *
     * @param jdbcUrl 模拟连接返回的 JDBC URL
     * @return 可被 Flyway 自动配置读取元数据的数据源
     * @throws SQLException 模拟 JDBC 元数据时抛出
     */
    private DataSource mockDataSource(String jdbcUrl) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(jdbcUrl);
        return dataSource;
    }

    /**
     * 获取 Flyway 配置中的迁移目录描述。
     *
     * @param flyway Flyway 实例
     * @return 迁移目录描述数组
     */
    private String[] getLocationDescriptors(Flyway flyway) {
        return Arrays.stream(flyway.getConfiguration().getLocations())
                .map(Location::getDescriptor)
                .toArray(String[]::new);
    }
}
