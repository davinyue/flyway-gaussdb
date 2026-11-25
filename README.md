# Flyway GaussDB 商业版适配器

本项目为 Flyway 8.5.13 提供华为 GaussDB 商业版适配，支持 JDK 8。Flyway 通过 Java SPI 自动加载适配器，使用 `jdbc:gaussdb:` 识别连接，复用 PostgreSQL 迁移方言，并按 GaussDB 规则处理 JDBC 兼容版本和会话角色恢复。

## 构建和接入

```bash
mvn clean install
```

在业务项目中添加依赖：

```xml
<dependency>
    <groupId>org.rdlinux</groupId>
    <artifactId>flyway-gaussdb</artifactId>
    <version>0.1.0</version>
</dependency>
```

应用需在运行时提供与目标数据库匹配的华为 JDBC 驱动 `com.huawei.gaussdb.jdbc.Driver`。适配器 JAR 仅包含适配代码和 SPI 注册，业务项目提供自己的迁移 SQL。

Spring Boot 2.7 应显式指定 GaussDB 的迁移目录。以下示例使用 `db/migration/gaussdb`：

```yaml
spring:
  datasource:
    driver-class-name: com.huawei.gaussdb.jdbc.Driver
    url: ${GAUSSDB_JDBC_URL}
    username: ${GAUSSDB_USERNAME}
    password: ${GAUSSDB_PASSWORD}
  flyway:
    locations: classpath:db/migration/gaussdb
```

直接使用 Flyway Java API 时，可通过 `Flyway.configure().dataSource(jdbcUrl, username, password).locations("classpath:db/migration/gaussdb").load().migrate()` 运行迁移。

适配器依赖 Flyway 8.5.13 的内部数据库接口，升级 Flyway 时需重新验证。真库验证覆盖华为 GaussDB 的 A 兼容模式。

## 发布到 Maven Central

普通构建会生成主 JAR、源码 JAR 和 Javadoc JAR。`release` profile 启用 GPG 签名及 Central Portal 发布，需要 Maven 3.9.2 或以上版本。

准备 GPG 签名密钥，将有权发布 `org.rdlinux` 的 Central Portal User Token 配置到 Maven `settings.xml` 的 `central` server。确认 POM 的项目地址和 SCM 指向实际仓库。

在本地生成并检查签名发布包：

```bash
mvn -Prelease -DskipUpload=true clean deploy
```

发布包位于 `target/central-publishing/central-bundle.zip`。执行 `mvn -Prelease clean deploy` 会将发布包上传到 Central Portal 验证，随后在 Portal 中手动确认发布。[Sonatype Maven 发布文档](https://central.sonatype.org/publish/publish-portal-maven/)
