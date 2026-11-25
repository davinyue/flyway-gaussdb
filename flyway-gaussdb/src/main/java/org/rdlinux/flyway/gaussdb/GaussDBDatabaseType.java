package org.rdlinux.flyway.gaussdb;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.postgresql.PostgreSQLDatabase;
import org.flywaydb.core.internal.database.postgresql.PostgreSQLDatabaseType;
import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 识别使用华为 JDBC 驱动的 GaussDB，并复用 Flyway 的 PostgreSQL 数据库实现。
 */
public final class GaussDBDatabaseType extends PostgreSQLDatabaseType {

    /** GaussDB JDBC URL 前缀。 */
    private static final String JDBC_URL_PREFIX = "jdbc:gaussdb:";

    /** 华为 GaussDB JDBC 驱动类名。 */
    private static final String DRIVER_CLASS_NAME = "com.huawei.gaussdb.jdbc.Driver";

    /** 数据库类型名称。 */
    private static final String DATABASE_NAME = "GaussDB";

    /**
     * @return 用于 Flyway 日志与诊断的数据库名称
     */
    @Override
    public String getName() {
        return DATABASE_NAME;
    }

    /**
     * @return 高于 Flyway 8.5.13 内置 PostgreSQL 系数据库的识别优先级
     */
    @Override
    public int getPriority() {
        return 2;
    }

    /**
     * 判断 URL 是否使用 GaussDB JDBC 协议。
     *
     * @param url JDBC URL
     * @return URL 使用 GaussDB JDBC 协议时为 true
     */
    @Override
    public boolean handlesJDBCUrl(String url) {
        return url != null && url.startsWith(JDBC_URL_PREFIX);
    }

    /**
     * 返回 Flyway 按 URL 建立连接时使用的驱动。
     *
     * @param url 已识别的 JDBC URL
     * @param classLoader Flyway 用于加载驱动的类加载器
     * @return 华为 GaussDB JDBC 驱动类名
     */
    @Override
    public String getDriverClass(String url, ClassLoader classLoader) {
        return DRIVER_CLASS_NAME;
    }

    /**
     * 通过连接 URL 区分 GaussDB 和 PostgreSQL。华为驱动的产品名随 driverInfoMode 变化。
     *
     * @param databaseProductName JDBC 元数据返回的数据库产品名
     * @param databaseProductVersion JDBC 元数据返回的数据库版本
     * @param connection Flyway 获取的 JDBC 连接
     * @return 连接使用 GaussDB JDBC 协议时为 true
     */
    @Override
    public boolean handlesDatabaseProductNameAndVersion(String databaseProductName, String databaseProductVersion,
                                                        Connection connection) {
        if (!databaseProductName.startsWith(DATABASE_NAME) && !databaseProductName.startsWith("PostgreSQL")) {
            return false;
        }
        try {
            return handlesJDBCUrl(connection.getMetaData().getURL());
        } catch (SQLException e) {
            throw new FlywaySqlException("Unable to determine the GaussDB JDBC URL", e);
        }
    }

    /**
     * 创建使用 GaussDB 版本规则的数据库对象。
     *
     * @param configuration Flyway 配置
     * @param jdbcConnectionFactory JDBC 连接工厂
     * @param statementInterceptor Flyway SQL 语句拦截器
     * @return GaussDB 数据库对象
     */
    @Override
    public PostgreSQLDatabase createDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory,
                                             StatementInterceptor statementInterceptor) {
        return new GaussDBDatabase(configuration, jdbcConnectionFactory, statementInterceptor);
    }
}
