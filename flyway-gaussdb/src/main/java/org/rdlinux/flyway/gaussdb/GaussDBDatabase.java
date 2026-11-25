package org.rdlinux.flyway.gaussdb;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.database.postgresql.PostgreSQLConnection;
import org.flywaydb.core.internal.database.postgresql.PostgreSQLDatabase;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 复用 PostgreSQL 迁移方言，并按 GaussDB 的 JDBC 元数据解释版本。
 */
final class GaussDBDatabase extends PostgreSQLDatabase {

    /**
     * 创建 GaussDB 数据库对象。
     *
     * @param configuration Flyway 配置
     * @param jdbcConnectionFactory JDBC 连接工厂
     * @param statementInterceptor Flyway SQL 语句拦截器
     */
    GaussDBDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory,
                    StatementInterceptor statementInterceptor) {
        super(configuration, jdbcConnectionFactory, statementInterceptor);
    }

    /**
     * GaussDB JDBC 上报的 9.2 是 PostgreSQL 兼容版本，不能用于判断 GaussDB 的内核支持范围。
     */
    @Override
    public void ensureSupported() {
        notifyDatabaseIsNotFormallySupported();
    }

    /**
     * 包装迁移使用的 JDBC 连接。
     *
     * @param connection JDBC 连接
     * @return 支持 GaussDB 角色复位的连接
     */
    @Override
    protected PostgreSQLConnection doGetConnection(Connection connection) {
        return new GaussDBConnection(this, connection);
    }

    /**
     * 复用 PostgreSQL 的 Schema 和迁移锁操作，按 GaussDB 语法复位角色。
     */
    private static final class GaussDBConnection extends PostgreSQLConnection {

        /**
         * 创建 GaussDB 迁移连接。
         *
         * @param database GaussDB 数据库对象
         * @param connection JDBC 连接
         */
        private GaussDBConnection(GaussDBDatabase database, Connection connection) {
            super(database, connection);
        }

        /**
         * 恢复登录角色。GaussDB 使用 SET ROLE 指定角色时可能要求提供密码。
         *
         * @throws SQLException 数据库拒绝复位角色时抛出
         */
        @Override
        protected void doRestoreOriginalState() throws SQLException {
            jdbcTemplate.execute("RESET ROLE");
        }
    }
}
