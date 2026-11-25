package org.rdlinux.flyway.gaussdb;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.internal.database.DatabaseType;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 GaussDB 适配器的 SPI 发现、连接识别和会话兼容行为。
 */
class GaussDBDatabaseTypeTest {

    /** 测试用 GaussDB JDBC URL。 */
    private static final String GAUSSDB_JDBC_URL = "jdbc:gaussdb://localhost:8000/testdb";

    /**
     * Flyway 应加载 SPI，并识别华为驱动两种产品名下的 GaussDB 连接。
     *
     * @throws SQLException 模拟 JDBC 元数据时抛出的异常
     */
    @Test
    void shouldDiscoverGaussDBThroughSpi() throws SQLException {
        DatabaseType databaseType = DatabaseTypeRegister.getDatabaseTypeForUrl(GAUSSDB_JDBC_URL);
        assertEquals(GaussDBDatabaseType.class, databaseType.getClass());
        assertEquals("com.huawei.gaussdb.jdbc.Driver",
                databaseType.getDriverClass(GAUSSDB_JDBC_URL, getClass().getClassLoader()));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseMajorVersion()).thenReturn(9);
        when(metadata.getDatabaseMinorVersion()).thenReturn(2);
        when(metadata.getDatabaseProductVersion()).thenReturn("9.2");
        when(metadata.getURL()).thenReturn(GAUSSDB_JDBC_URL);
        when(metadata.getDatabaseProductName()).thenReturn("GaussDB", "PostgreSQL");

        assertSame(databaseType, DatabaseTypeRegister.getDatabaseTypeForConnection(connection));
        assertSame(databaseType, DatabaseTypeRegister.getDatabaseTypeForConnection(connection));
    }

    /**
     * PostgreSQL 连接应继续由 Flyway 自带的 PostgreSQL 类型处理。
     *
     * @throws SQLException 模拟 JDBC 元数据和版本查询时抛出的异常
     */
    @Test
    void shouldKeepPostgreSQLTypeForPostgreSQLConnections() throws SQLException {
        String url = "jdbc:postgresql://localhost:5432/testdb";
        assertFalse(new GaussDBDatabaseType().handlesJDBCUrl(url));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet version = mock(ResultSet.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn(url);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getDatabaseMajorVersion()).thenReturn(9);
        when(metadata.getDatabaseMinorVersion()).thenReturn(2);
        when(metadata.getDatabaseProductVersion()).thenReturn("9.2");
        when(connection.prepareStatement("SELECT version()")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(version);
        when(version.next()).thenReturn(true);
        when(version.getString(1)).thenReturn("PostgreSQL 9.2");

        assertEquals("PostgreSQL", DatabaseTypeRegister.getDatabaseTypeForConnection(connection).getName());
    }

    /**
     * 允许华为驱动上报的 9.2 兼容版本，复位时使用 GaussDB 登录角色。
     *
     * @throws SQLException 模拟 JDBC 元数据和语句时抛出的异常
     */
    @Test
    void shouldAcceptCompatibilityVersionAndResetRole() throws SQLException {
        DatabaseType databaseType = DatabaseTypeRegister.getDatabaseTypeForUrl(GAUSSDB_JDBC_URL);
        JdbcConnectionFactory connectionFactory = mock(JdbcConnectionFactory.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connectionFactory.getDatabaseType()).thenReturn(databaseType);
        when(connectionFactory.openConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseMajorVersion()).thenReturn(9);
        when(metadata.getDatabaseMinorVersion()).thenReturn(2);

        PreparedStatement currentState = mock(PreparedStatement.class);
        ResultSet currentStateResult = mock(ResultSet.class);
        when(connection.prepareStatement("SHOW search_path")).thenReturn(currentState);
        when(connection.prepareStatement("SELECT CURRENT_USER")).thenReturn(currentState);
        when(currentState.executeQuery()).thenReturn(currentStateResult);
        when(currentStateResult.next()).thenReturn(true);
        when(currentStateResult.getString(1)).thenReturn("test_user");
        PreparedStatement resetRole = mock(PreparedStatement.class);
        when(connection.prepareStatement("RESET ROLE")).thenReturn(resetRole);

        Database<?> database = databaseType.createDatabase(new ClassicConfiguration(), connectionFactory, null);
        assertDoesNotThrow(database::ensureSupported);
        database.getMainConnection().restoreOriginalState();
        verify(resetRole).execute();
    }
}
