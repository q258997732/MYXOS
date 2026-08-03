# MYTOS 设备自监控与运维系统实施计划

> **给代理工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐步实施。步骤使用复选框 `- [ ]` 语法以便跟踪。

**目标：** 将现有的单模块 Spring Boot 4.x / Java 17 项目改造为基于 JDK 1.8 + Spring Boot 2.7.x 的多模块 Maven 项目，实现对 MYTOS 设备的自监控、阈值自动运维、手动操作、设备托管、JWT 鉴权和数据定时清理。

**架构：** 采用 `myxos-common`（公共枚举/工具）、`myxos-domain`（实体/Mapper/审计）、`myxos-mytos-client`（设备 HTTP 客户端）、`myxos-main-service`（Web/鉴权/管理/前端）、`myxos-collector-service`（采集/阈值判定/动作执行/清理）五模块结构。主服务与采集服务通过共享 MySQL 数据库（任务队列表）和可选的内部 REST 接口通信，避免引入消息队列等额外基础设施。

**技术栈：** Spring Boot 2.7.18、JDK 1.8、MyBatis Plus 3.5.7、MySQL 8.0、jjwt 0.11.5、OkHttp 4.12.0、Flyway 8.5.x、Vue 3 + Element Plus。

---

## 一、多模块项目结构

```
F:\java workspace\MYXOS\
├── pom.xml                                # 父 POM
├── myxos-common\                         # 公共枚举、常量、工具类、统一响应
├── myxos-domain\                          # 实体、Mapper、MyBatis-Plus 配置、审计填充
├── myxos-mytos-client\                    # MYTOS 设备 HTTP API 客户端
├── myxos-main-service\                    # Web 服务、JWT 鉴权、REST API、前端静态资源
└── myxos-collector-service\               # 定时采集、阈值判定、动作执行、数据清理
```

模块依赖关系：
- `myxos-main-service` 依赖 `myxos-domain`、`myxos-mytos-client`、`myxos-common`
- `myxos-collector-service` 依赖 `myxos-domain`、`myxos-mytos-client`、`myxos-common`
- `myxos-domain` 依赖 `myxos-common`
- `myxos-mytos-client` 依赖 `myxos-common`

---

## 二、数据库表结构（所有表均含审计字段）

每张表必须包含以下字段：

```sql
who_created   VARCHAR(64)  NOT NULL DEFAULT '',
when_created  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
who_modified  VARCHAR(64)  NOT NULL DEFAULT '',
when_modified DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
deleted       TINYINT      NOT NULL DEFAULT 0
```

核心表清单：
- `sys_user`：用户、密码（BCrypt）、角色、状态
- `device`：设备 IP、端口、模式（桥接/NAT）、型号、状态、来源
- `device_group`：设备分组
- `metric_snapshot`：采集到的性能指标（高容量表）
- `threshold_rule`：阈值规则
- `threshold_action`：阈值触发的动作（日志/操作）
- `alarm_event`：告警事件
- `op_task`：操作任务队列（手动/自动）
- `action_log`：动作与系统日志
- `discover_task`：网段发现任务
- `sys_config`：系统配置（采集间隔、保留天数、清理周期、内部令牌）
- `login_token`：JWT 令牌吊销记录

---

## 三、实施任务

### 任务 1：重写父 POM 为多模块结构

**文件：**
- 修改：`F:\java workspace\MYXOS\pom.xml`
- 删除：`src/main/java/bob/myxos/MyxosApplication.java`
- 删除：`src/main/resources/application.properties`
- 删除：`src/test/java/bob/myxos/MyxosApplicationTests.java`

- [ ] **步骤 1：将根 pom.xml 改为父 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>bob.myxos</groupId>
    <artifactId>myxos-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>MYXOS Parent</name>

    <modules>
        <module>myxos-common</module>
        <module>myxos-domain</module>
        <module>myxos-mytos-client</module>
        <module>myxos-main-service</module>
        <module>myxos-collector-service</module>
    </modules>

    <properties>
        <java.version>1.8</java.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <jjwt.version>0.11.5</jjwt.version>
        <okhttp.version>4.12.0</okhttp.version>
        <flyway.version>8.5.13</flyway.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-boot-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>com.squareup.okhttp3</groupId>
                <artifactId>okhttp</artifactId>
                <version>${okhttp.version}</version>
            </dependency>
            <dependency>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-core</artifactId>
                <version>${flyway.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>2.7.18</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **步骤 2：删除旧的单模块源码**

运行命令：

```bash
rm -rf "F:\java workspace\MYXOS\src"
```

- [ ] **步骤 3：提交**

```bash
git add pom.xml
git commit -m "chore: convert to multi-module Maven project with Spring Boot 2.7.18 and JDK 1.8"
```

---

### 任务 2：创建 myxos-common 模块

**文件：**
- 创建：`myxos-common/pom.xml`
- 创建：`myxos-common/src/main/java/bob/myxos/common/api/Result.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/api/PageResult.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/DeviceStatus.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/DeviceMode.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/MetricType.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/CompareOp.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/ActionType.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/LogLevel.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/OpTaskStatus.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/OperationCode.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/ScopeType.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/util/IpUtils.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/util/JsonUtils.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/exception/BizException.java`

- [ ] **步骤 1：创建模块 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bob.myxos</groupId>
        <artifactId>myxos-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myxos-common</artifactId>
    <name>myxos-common</name>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：创建统一响应类**

```java
package bob.myxos.common.api;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("ok");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String msg) {
        Result<T> r = new Result<>();
        r.setCode(500);
        r.setMsg(msg);
        return r;
    }

    public static <T> Result<T> fail(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }
}
```

```java
package bob.myxos.common.api;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private Long total;
    private Long pages;
    private Long current;
    private Long size;
    private List<T> records;
}
```

- [ ] **步骤 3：创建核心枚举**

```java
package bob.myxos.common.enums;

public enum DeviceStatus {
    ONLINE, OFFLINE, UNKNOWN, DISABLED
}
```

```java
package bob.myxos.common.enums;

public enum DeviceMode {
    BRIDGE, NAT
}
```

```java
package bob.myxos.common.enums;

public enum MetricType {
    CPU, MEM, DISK, NET_RX, NET_TX, TEMP, UPTIME, VERSION, CUSTOM
}
```

```java
package bob.myxos.common.enums;

public enum CompareOp {
    GT, GTE, LT, LTE, EQ, NE
}
```

```java
package bob.myxos.common.enums;

public enum ActionType {
    LOG, OPERATION
}
```

```java
package bob.myxos.common.enums;

public enum LogLevel {
    DEBUG, INFO, WARN, ERROR
}
```

```java
package bob.myxos.common.enums;

public enum OpTaskStatus {
    PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT
}
```

```java
package bob.myxos.common.enums;

public enum OperationCode {
    REBOOT_HOST,
    RUN_ANDROID,
    STOP_ANDROID,
    REBOOT_ANDROID,
    RESET_ANDROID,
    RENAME_ANDROID,
    SET_CLIPBOARD,
    GET_CLIPBOARD,
    SET_LANGUAGE,
    REFRESH_LOCATION,
    SCREENSHOT,
    SHELL_ADB
}
```

```java
package bob.myxos.common.enums;

public enum ScopeType {
    ALL, GROUP, DEVICE
}
```

- [ ] **步骤 4：创建 IpUtils 支持 CIDR 展开**

```java
package bob.myxos.common.util;

import java.util.ArrayList;
import java.util.List;

public final class IpUtils {
    private IpUtils() {}

    public static List<String> expandCidr(String cidr) {
        if (cidr == null || !cidr.contains("/")) {
            throw new IllegalArgumentException("CIDR 格式错误，示例：192.168.30.0/24");
        }
        String[] parts = cidr.split("/");
        String baseIp = parts[0];
        int prefix = Integer.parseInt(parts[1]);
        if (prefix < 22) {
            throw new IllegalArgumentException("出于安全考虑，仅支持 /22 及以上网段");
        }
        int hostBits = 32 - prefix;
        int count = 1 << hostBits;
        int base = ipv4ToInt(baseIp);
        List<String> result = new ArrayList<>(count);
        for (int i = 1; i < count - 1; i++) {
            result.add(intToIpv4(base | i));
        }
        return result;
    }

    private static int ipv4ToInt(String ip) {
        String[] parts = ip.split("\\.");
        int value = 0;
        for (String part : parts) {
            value = (value << 8) | Integer.parseInt(part);
        }
        return value;
    }

    private static String intToIpv4(int value) {
        return String.format("%d.%d.%d.%d",
                (value >> 24) & 0xFF,
                (value >> 16) & 0xFF,
                (value >> 8) & 0xFF,
                value & 0xFF);
    }
}
```

- [ ] **步骤 5：创建业务异常**

```java
package bob.myxos.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **步骤 6：编译验证**

运行命令：

```bash
cd "F:\java workspace\MYXOS"
mvn -pl myxos-common clean install -DskipTests
```

期望输出：BUILD SUCCESS

- [ ] **步骤 7：提交**

```bash
git add myxos-common/
git commit -m "feat(common): add shared enums, Result, IpUtils and exceptions"
```

---

### 任务 3：创建 myxos-domain 模块

**文件：**
- 创建：`myxos-domain/pom.xml`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/SysUser.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/Device.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/DeviceGroup.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/MetricSnapshot.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/ThresholdRule.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/ThresholdAction.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/AlarmEvent.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/OpTask.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/ActionLog.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/DiscoverTask.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/SysConfig.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/LoginToken.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/mapper/` 下所有 Mapper 接口
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/audit/LoginUserHolder.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/audit/AuditMetaObjectHandler.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/config/MybatisPlusConfig.java`

- [ ] **步骤 1：创建模块 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bob.myxos</groupId>
        <artifactId>myxos-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myxos-domain</artifactId>
    <name>myxos-domain</name>

    <dependencies>
        <dependency>
            <groupId>bob.myxos</groupId>
            <artifactId>myxos-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：创建 LoginUserHolder**

```java
package bob.myxos.domain.audit;

public final class LoginUserHolder {
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private LoginUserHolder() {}

    public static void set(String username) {
        USERNAME.set(username);
    }

    public static String get() {
        String name = USERNAME.get();
        return name == null ? "system" : name;
    }

    public static void clear() {
        USERNAME.remove();
    }
}
```

- [ ] **步骤 3：创建 AuditMetaObjectHandler**

```java
package bob.myxos.domain.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        String username = LoginUserHolder.get();
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "whoCreated", String.class, username);
        this.strictInsertFill(metaObject, "whenCreated", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "whoModified", String.class, username);
        this.strictInsertFill(metaObject, "whenModified", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "whoModified", String.class, LoginUserHolder.get());
        this.strictUpdateFill(metaObject, "whenModified", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **步骤 4：创建实体示例（SysUser）**

```java
package bob.myxos.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private String whoCreated;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime whenCreated;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String whoModified;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime whenModified;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
```

- [ ] **步骤 5：创建所有实体和 Mapper**

按同样的模式创建 `Device`、`DeviceGroup`、`MetricSnapshot`、`ThresholdRule`、`ThresholdAction`、`AlarmEvent`、`OpTask`、`ActionLog`、`DiscoverTask`、`SysConfig`、`LoginToken`，以及对应的 `BaseMapper<T>` 接口。

- [ ] **步骤 6：创建 MybatisPlusConfig**

```java
package bob.myxos.domain.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **步骤 7：编译验证**

```bash
cd "F:\java workspace\MYXOS"
mvn -pl myxos-domain clean install -DskipTests
```

期望：BUILD SUCCESS

- [ ] **步骤 8：提交**

```bash
git add myxos-domain/
git commit -m "feat(domain): add entities, mappers, audit handler and MyBatis-Plus config"
```

---

### 任务 4：数据库迁移脚本

**文件：**
- 创建：`myxos-main-service/src/main/resources/db/migration/V1__init.sql`
- 创建：`myxos-main-service/src/main/resources/db/migration/V2__seed.sql`

- [ ] **步骤 1：创建 V1__init.sql**

```sql
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(64),
    role VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
    status TINYINT NOT NULL DEFAULT 1,
    last_login_at DATETIME,
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS device_group (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT UNSIGNED DEFAULT 0,
    remark VARCHAR(255),
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS device (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    ip VARCHAR(64) NOT NULL,
    port INT NOT NULL,
    host_ip VARCHAR(64),
    instance_index INT,
    mode VARCHAR(16) NOT NULL DEFAULT 'BRIDGE',
    model VARCHAR(16),
    group_id BIGINT UNSIGNED DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    version VARCHAR(64),
    last_seen_at DATETIME,
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    remark VARCHAR(255),
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ip_port (ip, port),
    INDEX idx_group (group_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS metric_snapshot (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT UNSIGNED NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    metric_value VARCHAR(255) NOT NULL,
    metric_num DECIMAL(18,4),
    extra JSON,
    collected_at DATETIME NOT NULL,
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_device_time (device_id, collected_at),
    INDEX idx_type_time (metric_type, collected_at),
    INDEX idx_collected_at (collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS threshold_rule (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    compare_op VARCHAR(8) NOT NULL,
    threshold_value DECIMAL(18,4) NOT NULL,
    trigger_mode VARCHAR(16) NOT NULL DEFAULT 'DURATION',
    duration_sec INT NOT NULL DEFAULT 0,
    consecutive_count INT NOT NULL DEFAULT 0,
    scope_type VARCHAR(16) NOT NULL DEFAULT 'ALL',
    scope_id BIGINT UNSIGNED,
    enabled TINYINT NOT NULL DEFAULT 1,
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_metric (metric_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS threshold_action (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT UNSIGNED NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    log_level VARCHAR(16),
    operation_code VARCHAR(64),
    operation_params JSON,
    sort INT NOT NULL DEFAULT 0,
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alarm_event (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    metric_value VARCHAR(255) NOT NULL,
    threshold_value VARCHAR(64) NOT NULL,
    fired_at DATETIME NOT NULL,
    resolved_at DATETIME,
    status VARCHAR(16) NOT NULL DEFAULT 'FIRING',
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_device_time (device_id, fired_at),
    INDEX idx_status (status),
    INDEX idx_fired_at (fired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS op_task (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT UNSIGNED NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    params JSON,
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    source_ref_id BIGINT UNSIGNED,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    scheduled_at DATETIME NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    result_msg VARCHAR(1024),
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_status_sched (status, scheduled_at),
    INDEX idx_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS action_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED,
    alarm_id BIGINT UNSIGNED,
    device_id BIGINT UNSIGNED NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    log_level VARCHAR(16),
    message TEXT,
    created_at DATETIME NOT NULL,
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_device_time (device_id, created_at),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS discover_task (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    cidr VARCHAR(64) NOT NULL,
    port_from INT NOT NULL,
    port_to INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    found_count INT NOT NULL DEFAULT 0,
    started_at DATETIME,
    finished_at DATETIME,
    message VARCHAR(512),
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value VARCHAR(1024) NOT NULL,
    description VARCHAR(255),
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS login_token (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    token_id VARCHAR(64) NOT NULL UNIQUE,
    issued_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked TINYINT NOT NULL DEFAULT 0,
    who_created VARCHAR(64) NOT NULL DEFAULT '',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT '',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **步骤 2：创建 V2__seed.sql**

```sql
INSERT INTO device_group (name, parent_id, remark, who_created, who_modified)
VALUES ('默认分组', 0, '系统自动创建', 'system', 'system');

-- 密码为 BCrypt 加密后的 admin123
INSERT INTO sys_user (username, password, nickname, role, status, who_created, who_modified)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '管理员', 'ADMIN', 1, 'system', 'system');

INSERT INTO sys_config (config_key, config_value, description, who_created, who_modified) VALUES
('collect.interval.sec', '30', '采集间隔（秒）', 'system', 'system'),
('metric.retention.days', '7', '指标数据保留天数', 'system', 'system'),
('log.retention.days', '30', '日志保留天数', 'system', 'system'),
('alarm.retention.days', '90', '告警保留天数', 'system', 'system'),
('cleanup.cron', '0 0 3 * * ?', '数据清理定时表达式', 'system', 'system'),
('internal.token', REPLACE(UUID(), '-', ''), '主服务与采集服务内部通信令牌', 'system', 'system');
```

- [ ] **步骤 3：提交**

```bash
git add myxos-main-service/src/main/resources/db/migration/
git commit -m "feat(db): add Flyway migration scripts for all tables and seed data"
```

---

### 任务 5：创建 myxos-mytos-client 模块

**文件：**
- 创建：`myxos-mytos-client/pom.xml`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/MytosClient.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/MytosClientFactory.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/MytosException.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/config/MytosClientConfig.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/MytosBaseResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/HealthResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/HostSystemInfoResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/HardwareCfgResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/HostVerResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/NetworkDetailResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/MytDeviceListResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/AndroidListResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/AndroidDetailResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/BootStatusResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/ScreenshotResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/ShellResp.java`
- 创建：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/ClipboardResp.java`
- 删除：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/InfoResp.java`（旧版无对应接口）
- 删除：`myxos-mytos-client/src/main/java/bob/myxos/mytos/dto/VersionResp.java`（旧版无对应接口）

- [ ] **步骤 1：创建模块 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bob.myxos</groupId>
        <artifactId>myxos-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myxos-mytos-client</artifactId>
    <name>myxos-mytos-client</name>

    <dependencies>
        <dependency>
            <groupId>bob.myxos</groupId>
            <artifactId>myxos-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：创建 OkHttp 配置**

```java
package bob.myxos.mytos.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MytosClientConfig {

    @Bean
    public OkHttpClient mytosOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(32, 5, TimeUnit.MINUTES))
                .dispatcher(new okhttp3.Dispatcher())
                .build();
    }
}
```

- [ ] **步骤 3：创建基础响应 DTO**

```java
package bob.myxos.mytos.dto;

import lombok.Data;

@Data
public class MytosBaseResp {
    private Integer code;
    private String msg;
    private String error;
    private String reason;
}
```

```java
package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HealthResp extends MytosBaseResp {
    private HealthData data;

    @Data
    public static class HealthData {
        private Boolean dockerApi;
        private Boolean pingStatus;
        private String hostIp;
    }
}
```

```java
package bob.myxos.mytos.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HostVerResp extends MytosBaseResp {
    private String msg;
}
```

- [ ] **步骤 4：创建 MytosClient 方法**

> 接口清单以 `docs/superpowers/plans/2026-08-03-mytos-api-mapping.md` 最终确认范围为准。以下为 `MytosClient` 需实现的方法骨架，具体 DTO 按返回结构补充。

```java
package bob.myxos.mytos;

import bob.myxos.mytos.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;

@RequiredArgsConstructor
public class MytosClient {
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    // ========== 主机层面 ==========

    public HealthResp healthcheck(String hostIp) throws IOException {
        return post("/host_api/v1/healthcheck/" + hostIp, HealthResp.class);
    }

    public HostSystemInfoResp getSystemInfo(String hostIp) throws IOException {
        return get("/host_api/v1/get_systeminfo/" + hostIp, HostSystemInfoResp.class);
    }

    public HostSystemInfoResp systeminfo() throws IOException {
        return get("/host_api/v1/systeminfo", HostSystemInfoResp.class);
    }

    public HardwareCfgResp getHardwareCfg() throws IOException {
        return get("/host_api/v1/get_hardware_cfg", HardwareCfgResp.class);
    }

    public HostVerResp getHostVer(String ip) throws IOException {
        return get("/dc_api/v1/get_host_ver/" + ip, HostVerResp.class);
    }

    public NetworkDetailResp getNetworkDetail(String ip) throws IOException {
        return get("/dc_api/v1/get_network_detail/" + ip, NetworkDetailResp.class);
    }

    public MytDeviceListResp queryMyt() throws IOException {
        return get("/host_api/v1/query_myt", MytDeviceListResp.class);
    }

    public MytosBaseResp rebootHost(String hostIp) throws IOException {
        return get("/host_api/v1/reboot_host/" + hostIp, MytosBaseResp.class);
    }

    // ========== 容器实例生命周期 ==========

    public AndroidListResp listAndroid(String ip) throws IOException {
        return get("/dc_api/v1/list/" + ip, AndroidListResp.class);
    }

    public AndroidDetailResp getAndroidDetail(String ip, String name) throws IOException {
        return get("/dc_api/v1/get_android_detail/" + ip + "/" + name, AndroidDetailResp.class);
    }

    public BootStatusResp getAndroidBootStatus(String ip, String name) throws IOException {
        return get("/and_api/v1/get_android_boot_status/" + ip + "/" + name, BootStatusResp.class);
    }

    public MytosBaseResp runAndroid(String ip, String name) throws IOException {
        return get("/dc_api/v1/run/" + ip + "/" + name, MytosBaseResp.class);
    }

    public MytosBaseResp stopAndroid(String ip, String name) throws IOException {
        return get("/dc_api/v1/stop/" + ip + "/" + name, MytosBaseResp.class);
    }

    public MytosBaseResp rebootAndroid(String ip, String name) throws IOException {
        return get("/dc_api/v1/reboot/" + ip + "/" + name, MytosBaseResp.class);
    }

    public MytosBaseResp resetAndroid(String ip, String name) throws IOException {
        return get("/dc_api/v1/reset/" + ip + "/" + name, MytosBaseResp.class);
    }

    public MytosBaseResp renameAndroid(String ip, String oldName, String newName) throws IOException {
        return get("/dc_api/v1/rename/" + ip + "/" + oldName + "/" + newName, MytosBaseResp.class);
    }

    // ========== 手动操作面板 ==========

    public ScreenshotResp screenshot(String ip, String name, String level) throws IOException {
        return get("/and_api/v1/screenshots/" + ip + "/" + name + "/" + level, ScreenshotResp.class);
    }

    public ShellResp shell(String ip, String name, String command) throws IOException {
        return postBody("/and_api/v1/shell/" + ip + "/" + name, command, ShellResp.class);
    }

    public MytosBaseResp clipboardSet(String ip, String name, String text) throws IOException {
        return get("/and_api/v1/clipboard_set/" + ip + "/" + name + "?text=" + text, MytosBaseResp.class);
    }

    public ClipboardResp clipboardGet(String ip, String name) throws IOException {
        return get("/and_api/v1/clipboard_get/" + ip + "/" + name, ClipboardResp.class);
    }

    public MytosBaseResp setLanguage(String ip, String name, String country, String language) throws IOException {
        return get("/and_api/v1/set_Language/" + ip + "/" + name + "/" + country + "/" + language, MytosBaseResp.class);
    }

    public MytosBaseResp setIpLocation(String ip, String name, String language) throws IOException {
        return get("/and_api/v1/set_ipLocation/" + ip + "/" + name + "/" + language, MytosBaseResp.class);
    }

    // ========== 基础 HTTP 工具方法 ==========

    private String url(String path) {
        return baseUrl + path;
    }

    private <T> T get(String path, Class<T> clazz) throws IOException {
        Request request = new Request.Builder().url(url(path)).get().build();
        return execute(request, clazz);
    }

    private <T> T post(String path, Class<T> clazz) throws IOException {
        Request request = new Request.Builder()
                .url(url(path))
                .post(RequestBody.create("", MediaType.parse("application/json")))
                .build();
        return execute(request, clazz);
    }

    private <T> T postBody(String path, String body, Class<T> clazz) throws IOException {
        Request request = new Request.Builder()
                .url(url(path))
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();
        return execute(request, clazz);
    }

    private <T> T execute(Request request, Class<T> clazz) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            return objectMapper.readValue(body, clazz);
        }
    }
}
```

**需同步创建的 DTO（在 `bob.myxos.mytos.dto` 包下）**：

- `MytosBaseResp`：通用返回（已有）
- `HealthResp`：健康检查返回 `docker_api`、`ping_status`、`host_ip`
- `HostSystemInfoResp`：主机 CPU/内存/磁盘等系统信息
- `HardwareCfgResp`：主机硬件配置
- `HostVerResp`：主机版本
- `NetworkDetailResp`：网络对象明细
- `MytDeviceListResp`：局域网在线 3588 设备列表
- `AndroidListResp`：安卓容器列表
- `AndroidDetailResp`：安卓实例详细信息
- `BootStatusResp`：安卓启动状态
- `ScreenshotResp`：截图返回（Base64 或图片流）
- `ShellResp`：adb shell 执行结果
- `ClipboardResp`：剪贴板内容

**注意**：旧的 `info()` 与 `queryVersion()` 方法在 `mytos_real_openapi.json` 中无直接对应路径，可移除或保留为兼容方法；自动发现统一使用 `healthcheck()`。

- [ ] **步骤 5：创建 MytosClientFactory**

```java
package bob.myxos.mytos;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MytosClientFactory {
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MytosClient create(String ip, int port) {
        return new MytosClient(httpClient, objectMapper, "http://" + ip + ":" + port);
    }
}
```

- [ ] **步骤 6：编译验证**

```bash
cd "F:\java workspace\MYXOS"
mvn -pl myxos-mytos-client clean install -DskipTests
```

期望：BUILD SUCCESS

- [ ] **步骤 7：提交**

```bash
git add myxos-mytos-client/
git commit -m "feat(mytos-client): add MYTOS device client for host/container monitoring and operations"
```

---

### 任务 6：创建 myxos-main-service 模块

**文件：**
- 创建：`myxos-main-service/pom.xml`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/MainServiceApplication.java`
- 创建：`myxos-main-service/src/main/resources/application.yml`

- [ ] **步骤 1：创建模块 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bob.myxos</groupId>
        <artifactId>myxos-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myxos-main-service</artifactId>
    <name>myxos-main-service</name>

    <dependencies>
        <dependency>
            <groupId>bob.myxos</groupId>
            <artifactId>myxos-domain</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>bob.myxos</groupId>
            <artifactId>myxos-mytos-client</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **步骤 2：创建启动类**

```java
package bob.myxos.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"bob.myxos.main", "bob.myxos.domain"})
public class MainServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainServiceApplication.class, args);
    }
}
```

- [ ] **步骤 3：创建 application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: myxos-main-service
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/myxos?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

myxos:
  jwt:
    secret: ${MYXOS_JWT_SECRET:}
    expiration: 7200000
    refresh-expiration: 604800000
  internal:
    token: ${MYXOS_INTERNAL_TOKEN:}
```

- [ ] **步骤 4：启动验证**

确保本地 MySQL 存在 `myxos` 数据库：

```bash
cd "F:\java workspace\MYXOS"
mvn -pl myxos-main-service spring-boot:run -DskipTests
```

期望：服务启动成功，Flyway 自动建表，控制台无报错。

- [ ] **步骤 5：提交**

```bash
git add myxos-main-service/
git commit -m "feat(main-service): add Spring Boot main service module with datasource and Flyway"
```

---

### 任务 7：创建 myxos-collector-service 模块

**文件：**
- 创建：`myxos-collector-service/pom.xml`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/CollectorServiceApplication.java`
- 创建：`myxos-collector-service/src/main/resources/application.yml`

- [ ] **步骤 1：创建模块 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>bob.myxos</groupId>
        <artifactId>myxos-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myxos-collector-service</artifactId>
    <name>myxos-collector-service</name>

    <dependencies>
        <dependency>
            <groupId>bob.myxos</groupId>
            <artifactId>myxos-domain</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>bob.myxos</groupId>
            <artifactId>myxos-mytos-client</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **步骤 2：创建启动类**

```java
package bob.myxos.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"bob.myxos.collector", "bob.myxos.domain"})
public class CollectorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollectorServiceApplication.class, args);
    }
}
```

- [ ] **步骤 3：创建 application.yml**

```yaml
server:
  port: 8081

spring:
  application:
    name: myxos-collector-service
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/myxos?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

myxos:
  internal:
    token: ${MYXOS_INTERNAL_TOKEN:}
  collector:
    interval-ms: 30000
    metric-pool-core: 8
    metric-pool-max: 32
    metric-pool-queue: 500
    op-pool-core: 4
    op-pool-max: 16
    op-pool-queue: 200
```

- [ ] **步骤 4：启动验证**

```bash
cd "F:\java workspace\MYXOS"
mvn -pl myxos-collector-service spring-boot:run -DskipTests
```

期望：服务启动成功。

- [ ] **步骤 5：提交**

```bash
git add myxos-collector-service/
git commit -m "feat(collector-service): add collector service module with scheduling enabled"
```

---

### 任务 8：实现 JWT 鉴权

**文件：**
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/security/JwtConfig.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/security/JwtTokenProvider.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/security/LoginUser.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/security/JwtAuthenticationFilter.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/security/UserDetailsServiceImpl.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/config/SecurityConfig.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/config/GlobalExceptionHandler.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/AuthController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/AuthService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/AuthServiceImpl.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/LoginReq.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/LoginResp.java`

- [ ] **步骤 1：创建 JwtConfig 属性类**

```java
package bob.myxos.main.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "myxos.jwt")
public class JwtConfig {
    private String secret;
    private Long expiration = 7200000L;
    private Long refreshExpiration = 604800000L;
}
```

- [ ] **步骤 2：创建 JwtTokenProvider**

```java
package bob.myxos.main.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtConfig jwtConfig;
    private Key key;

    @PostConstruct
    public void init() {
        if (jwtConfig.getSecret() == null || jwtConfig.getSecret().isEmpty()) {
            throw new IllegalStateException("环境变量 MYXOS_JWT_SECRET 不能为空");
        }
        this.key = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes());
    }

    public String generateToken(LoginUser loginUser) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getExpiration());
        return Jwts.builder()
                .setSubject(String.valueOf(loginUser.getUserId()))
                .claim("username", loginUser.getUsername())
                .claim("role", loginUser.getRole())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **步骤 3：创建 LoginUser 和 UserDetailsServiceImpl**

```java
package bob.myxos.main.security;

import lombok.Data;

@Data
public class LoginUser {
    private Long userId;
    private String username;
    private String role;
}
```

```java
package bob.myxos.main.security;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.SysUser;
import bob.myxos.domain.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, 0));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        if (user.getStatus() != 1) {
            throw new BizException("用户已被禁用");
        }
        return new User(user.getUsername(), user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }
}
```

- [ ] **步骤 4：创建 JwtAuthenticationFilter**

```java
package bob.myxos.main.security;

import bob.myxos.domain.audit.LoginUserHolder;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                Claims claims = jwtTokenProvider.parseToken(token);
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);
                LoginUserHolder.set(username);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null,
                                Collections.singletonList(() -> "ROLE_" + role));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } finally {
            LoginUserHolder.clear();
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

- [ ] **步骤 5：创建 SecurityConfig**

```java
package bob.myxos.main.config;

import bob.myxos.main.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/auth/login", "/", "/index.html", "/static/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated();
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **步骤 6：创建 AuthController 和 Service**

```java
package bob.myxos.main.dto;

import lombok.Data;

@Data
public class LoginReq {
    private String username;
    private String password;
}
```

```java
package bob.myxos.main.dto;

import lombok.Data;

@Data
public class LoginResp {
    private String token;
    private Long expiresIn;
    private String username;
    private String role;
}
```

```java
package bob.myxos.main.service;

import bob.myxos.main.dto.LoginReq;
import bob.myxos.main.dto.LoginResp;

public interface AuthService {
    LoginResp login(LoginReq req);
    void logout(Long userId);
}
```

```java
package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.audit.LoginUserHolder;
import bob.myxos.domain.entity.LoginToken;
import bob.myxos.domain.entity.SysUser;
import bob.myxos.domain.mapper.LoginTokenMapper;
import bob.myxos.domain.mapper.SysUserMapper;
import bob.myxos.main.dto.LoginReq;
import bob.myxos.main.dto.LoginResp;
import bob.myxos.main.security.JwtTokenProvider;
import bob.myxos.main.security.LoginUser;
import bob.myxos.main.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SysUserMapper sysUserMapper;
    private final LoginTokenMapper loginTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public LoginResp login(LoginReq req) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, req.getUsername())
                        .eq(SysUser::getDeleted, 0));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BizException("用户已被禁用");
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());
        String token = jwtTokenProvider.generateToken(loginUser);
        LoginHolder.set(user.getUsername());

        LoginToken loginToken = new LoginToken();
        loginToken.setUserId(user.getId());
        loginToken.setTokenId(UUID.randomUUID().toString());
        loginToken.setIssuedAt(LocalDateTime.now());
        loginToken.setExpiresAt(LocalDateTime.now().plusHours(2));
        loginTokenMapper.insert(loginToken);

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        LoginResp resp = new LoginResp();
        resp.setToken(token);
        resp.setExpiresIn(7200L);
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        return resp;
    }

    @Override
    public void logout(Long userId) {
        loginTokenMapper.revokeByUserId(userId);
    }
}
```

- [ ] **步骤 7：创建 AuthController**

```java
package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.main.dto.LoginReq;
import bob.myxos.main.dto.LoginResp;
import bob.myxos.main.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // 从 SecurityContext 取当前用户 ID 执行 logout
        return Result.ok();
    }
}
```

- [ ] **步骤 8：测试登录接口**

启动 main-service 后执行：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

期望返回：

```json
{"code":200,"msg":"ok","data":{"token":"eyJ...","expiresIn":7200,"username":"admin","role":"ADMIN"}}
```

- [ ] **步骤 9：提交**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/security/ \
        myxos-main-service/src/main/java/bob/myxos/main/config/SecurityConfig.java \
        myxos-main-service/src/main/java/bob/myxos/main/controller/AuthController.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/ \
        myxos-main-service/src/main/java/bob/myxos/main/dto/
git commit -m "feat(auth): implement JWT login/logout with Spring Security"
```

---

### 任务 9：实现设备与分组管理

**文件：**
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceGroupController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/DeviceGroupService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceGroupServiceImpl.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/DeviceService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/DeviceCreateReq.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/DeviceListResp.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/mapper/DeviceMapper.java`（扩展自定义 SQL）

- [ ] **步骤 1：创建 DeviceMapper 自定义查询**

```java
package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.Device;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface DeviceMapper extends BaseMapper<Device> {

    @Select("SELECT d.*, " +
            "(SELECT COUNT(*) FROM alarm_event a WHERE a.device_id = d.id AND a.status = 'FIRING' AND a.deleted = 0) AS alarmCount " +
            "FROM device d " +
            "WHERE d.deleted = 0 " +
            "ORDER BY d.when_created DESC")
    List<Map<String, Object>> selectDeviceListWithAlarmCount();
}
```

- [ ] **步骤 2：创建 DeviceService 及实现**

```java
package bob.myxos.main.service;

import bob.myxos.domain.entity.Device;
import bob.myxos.main.dto.DeviceCreateReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface DeviceService {
    Device createDevice(DeviceCreateReq req);
    Page<Device> listDevices(Long groupId, String status, String keyword, Long page, Long size);
    Device getDetail(Long id);
    void deleteDevice(Long id);
}
```

```java
package bob.myxos.main.service.impl;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {
    private final DeviceMapper deviceMapper;

    @Override
    public Device createDevice(DeviceCreateReq req) {
        Long count = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getIp, req.getIp())
                        .eq(Device::getPort, req.getPort())
                        .eq(Device::getDeleted, 0));
        if (count > 0) {
            throw new BizException("该 IP 和端口已存在");
        }
        Device device = new Device();
        device.setName(req.getName());
        device.setIp(req.getIp());
        device.setPort(req.getPort());
        device.setMode(req.getMode());
        device.setGroupId(req.getGroupId());
        device.setStatus(DeviceStatus.UNKNOWN.name());
        device.setSource("MANUAL");
        deviceMapper.insert(device);
        return device;
    }

    @Override
    public Page<Device> listDevices(Long groupId, String status, String keyword, Long page, Long size) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeleted, 0);
        if (groupId != null) {
            wrapper.eq(Device::getGroupId, groupId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Device::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Device::getName, keyword).or().like(Device::getIp, keyword));
        }
        wrapper.orderByDesc(Device::getWhenCreated);
        return deviceMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Device getDetail(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null || device.getDeleted() == 1) {
            throw new BizException("设备不存在");
        }
        return device;
    }

    @Override
    public void deleteDevice(Long id) {
        deviceMapper.deleteById(id);
    }
}
```

- [ ] **步骤 3：创建 DeviceController**

```java
package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.Device;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.service.DeviceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;

    @GetMapping
    public Result<PageResult<Device>> list(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<Device> p = deviceService.listDevices(groupId, status, keyword, page, size);
        PageResult<Device> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Device> create(@Valid @RequestBody DeviceCreateReq req) {
        return Result.ok(deviceService.createDevice(req));
    }

    @GetMapping("/{id}")
    public Result<Device> detail(@PathVariable Long id) {
        return Result.ok(deviceService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return Result.ok();
    }
}
```

- [ ] **步骤 4：测试设备接口**

```bash
# 登录获取 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 创建设备
curl -X POST http://localhost:8080/api/devices \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"测试设备","ip":"192.168.30.2","port":9082,"mode":"BRIDGE","groupId":1}'
```

- [ ] **步骤 5：提交**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceController.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/ \
        myxos-main-service/src/main/java/bob/myxos/main/dto/ \
        myxos-domain/src/main/java/bob/myxos/domain/mapper/DeviceMapper.java
git commit -m "feat(device): implement device and group management APIs"
```

---

### 任务 10：实现采集服务核心

**文件：**
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/config/ThreadPoolConfig.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/MetricCollector.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/MetricCollectJob.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/service/MetricPersistService.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/service/impl/MetricPersistServiceImpl.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/config/CollectorProperties.java`

- [ ] **步骤 1：创建线程池配置**

```java
package bob.myxos.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "myxos.collector")
public class CollectorProperties {
    private Long intervalMs = 30000L;
    private Integer metricPoolCore = 8;
    private Integer metricPoolMax = 32;
    private Integer metricPoolQueue = 500;
    private Integer opPoolCore = 4;
    private Integer opPoolMax = 16;
    private Integer opPoolQueue = 200;
}
```

```java
package bob.myxos.collector.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class ThreadPoolConfig {
    private final CollectorProperties properties;

    @Bean(name = "metricCollectExecutor")
    public ThreadPoolTaskExecutor metricCollectExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getMetricPoolCore());
        executor.setMaxPoolSize(properties.getMetricPoolMax());
        executor.setQueueCapacity(properties.getMetricPoolQueue());
        executor.setThreadNamePrefix("metric-collect-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "opTaskExecutor")
    public ThreadPoolTaskExecutor opTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getOpPoolCore());
        executor.setMaxPoolSize(properties.getOpPoolMax());
        executor.setQueueCapacity(properties.getOpPoolQueue());
        executor.setThreadNamePrefix("op-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

- [ ] **步骤 2：创建 MetricCollector**

```java
package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.enums.MetricType;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.HealthResp;
import bob.myxos.mytos.dto.HostVerResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class MetricCollector implements Runnable {
    private final Device device;
    private final MytosClientFactory clientFactory;
    private final MetricPersistCallback persistCallback;
    private final DeviceMapper deviceMapper;

    public interface MetricPersistCallback {
        void persist(List<MetricSnapshot> snapshots);
    }

    @Override
    public void run() {
        MytosClient client = clientFactory.create(device.getIp(), device.getPort());
        List<MetricSnapshot> snapshots = new ArrayList<>();
        DeviceStatus status = DeviceStatus.OFFLINE;
        String version = null;
        try {
            HealthResp health = client.healthcheck(device.getIp());
            if (health.getCode() != null && health.getCode() == 200) {
                status = DeviceStatus.ONLINE;
                HostVerResp verResp = client.getHostVer(device.getIp());
                version = verResp.getMsg();
                MetricSnapshot s = new MetricSnapshot();
                s.setDeviceId(device.getId());
                s.setMetricType(MetricType.VERSION.name());
                s.setMetricValue(version);
                s.setCollectedAt(LocalDateTime.now());
                snapshots.add(s);
            }
        } catch (Exception e) {
            log.warn("采集设备失败 {}:{}", device.getIp(), device.getPort(), e);
            status = DeviceStatus.OFFLINE;
        }

        Device update = new Device();
        update.setId(device.getId());
        update.setStatus(status.name());
        update.setVersion(version);
        update.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(update);

        if (!snapshots.isEmpty()) {
            persistCallback.persist(snapshots);
        }
    }
}
```

- [ ] **步骤 3：创建 MetricPersistService**

```java
package bob.myxos.collector.service;

import bob.myxos.domain.entity.MetricSnapshot;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface MetricPersistService extends IService<MetricSnapshot> {
    void saveBatchSnapshots(List<MetricSnapshot> snapshots);
}
```

```java
package bob.myxos.collector.service.impl;

import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import bob.myxos.collector.service.MetricPersistService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MetricPersistServiceImpl extends ServiceImpl<MetricSnapshotMapper, MetricSnapshot>
        implements MetricPersistService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchSnapshots(List<MetricSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        saveBatch(snapshots, 500);
    }
}
```

- [ ] **步骤 4：创建 MetricCollectJob**

```java
package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.collector.service.MetricPersistService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricCollectJob {
    private final DeviceMapper deviceMapper;
    private final MytosClientFactory clientFactory;
    private final MetricPersistService metricPersistService;

    @Resource(name = "metricCollectExecutor")
    private ThreadPoolExecutor metricCollectExecutor;

    private final ConcurrentHashMap<Long, Boolean> inFlight = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${myxos.collector.interval-ms:30000}")
    public void collect() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .ne(Device::getStatus, DeviceStatus.DISABLED.name())
                        .eq(Device::getDeleted, 0));
        log.info("开始采集，设备数量：{}", devices.size());
        for (Device device : devices) {
            if (inFlight.putIfAbsent(device.getId(), Boolean.TRUE) != null) {
                continue;
            }
            metricCollectExecutor.execute(() -> {
                try {
                    MetricCollector collector = new MetricCollector(device, clientFactory,
                            snapshots -> metricPersistService.saveBatchSnapshots(snapshots),
                            deviceMapper);
                    collector.run();
                } finally {
                    inFlight.remove(device.getId());
                }
            });
        }
    }
}
```

- [ ] **步骤 5：测试采集任务**

启动 collector-service 后，在 `device` 表中插入一条测试设备记录，观察 `metric_snapshot` 表是否有数据写入。

```sql
INSERT INTO device (name, ip, port, status, source, who_created, who_modified)
VALUES ('测试设备', '127.0.0.1', 9082, 'UNKNOWN', 'MANUAL', 'system', 'system');
```

- [ ] **步骤 6：提交**

```bash
git add myxos-collector-service/src/main/java/bob/myxos/collector/config/ \
        myxos-collector-service/src/main/java/bob/myxos/collector/collector/ \
        myxos-collector-service/src/main/java/bob/myxos/collector/service/
git commit -m "feat(collector): implement metric collection with bounded thread pools"
```

---

### 任务 11：实现阈值判定与动作执行

**文件：**
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/evaluate/RuleCache.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/evaluate/ThresholdEvaluator.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/execute/ActionExecutor.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/execute/LogActionExecutor.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/execute/OperationActionExecutor.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/execute/OpTaskExecuteJob.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/execute/OpTaskRunner.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/internal/InternalController.java`

- [ ] **步骤 1：创建 RuleCache**

```java
package bob.myxos.collector.evaluate;

import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RuleCache {
    private final ThresholdRuleMapper ruleMapper;
    private final ThresholdActionMapper actionMapper;

    private volatile Map<String, List<RuleWithActions>> cache = Collections.emptyMap();

    @PostConstruct
    public void load() {
        refresh();
    }

    public synchronized void refresh() {
        List<ThresholdRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<ThresholdRule>()
                        .eq(ThresholdRule::getEnabled, 1)
                        .eq(ThresholdRule::getDeleted, 0));
        List<ThresholdAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<ThresholdAction>()
                        .eq(ThresholdAction::getDeleted, 0));
        Map<Long, List<ThresholdAction>> actionMap = actions.stream()
                .collect(Collectors.groupingBy(ThresholdAction::getRuleId));
        cache = rules.stream()
                .map(r -> new RuleWithActions(r, actionMap.getOrDefault(r.getId(), Collections.emptyList())))
                .collect(Collectors.groupingBy(RuleWithActions::getMetricType));
    }

    public List<RuleWithActions> getByMetricType(String metricType) {
        return cache.getOrDefault(metricType, Collections.emptyList());
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RuleWithActions {
        private ThresholdRule rule;
        private List<ThresholdAction> actions;

        public String getMetricType() {
            return rule.getMetricType();
        }
    }
}
```

- [ ] **步骤 2：创建 ThresholdEvaluator**

```java
package bob.myxos.collector.evaluate;

import bob.myxos.common.enums.CompareOp;
import bob.myxos.common.enums.MetricType;
import bob.myxos.domain.entity.*;
import bob.myxos.domain.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdEvaluator {
    private final RuleCache ruleCache;
    private final AlarmEventMapper alarmEventMapper;
    private final ActionLogMapper actionLogMapper;
    private final OpTaskMapper opTaskMapper;

    public void evaluate(Device device, List<MetricSnapshot> snapshots) {
        for (MetricSnapshot snapshot : snapshots) {
            List<RuleCache.RuleWithActions> rules = ruleCache.getByMetricType(snapshot.getMetricType());
            for (RuleCache.RuleWithActions raw : rules) {
                ThresholdRule rule = raw.getRule();
                if (!matchScope(rule, device)) {
                    continue;
                }
                boolean breached = compare(snapshot.getMetricNum(), rule.getThresholdValue(), CompareOp.valueOf(rule.getCompareOp()));
                if (breached) {
                    fireAlarm(rule, raw.getActions(), device, snapshot);
                } else {
                    resolveAlarmIfExists(rule, device);
                }
            }
        }
    }

    private boolean matchScope(ThresholdRule rule, Device device) {
        switch (rule.getScopeType()) {
            case "ALL": return true;
            case "DEVICE": return rule.getScopeId().equals(device.getId());
            case "GROUP": return rule.getScopeId().equals(device.getGroupId());
            default: return false;
        }
    }

    private boolean compare(BigDecimal value, BigDecimal threshold, CompareOp op) {
        if (value == null || threshold == null) return false;
        int cmp = value.compareTo(threshold);
        switch (op) {
            case GT: return cmp > 0;
            case GTE: return cmp >= 0;
            case LT: return cmp < 0;
            case LTE: return cmp <= 0;
            case EQ: return cmp == 0;
            case NE: return cmp != 0;
            default: return false;
        }
    }

    private void fireAlarm(ThresholdRule rule, List<ThresholdAction> actions, Device device, MetricSnapshot snapshot) {
        AlarmEvent event = new AlarmEvent();
        event.setRuleId(rule.getId());
        event.setDeviceId(device.getId());
        event.setMetricType(snapshot.getMetricType());
        event.setMetricValue(snapshot.getMetricValue());
        event.setThresholdValue(rule.getThresholdValue().toString());
        event.setFiredAt(LocalDateTime.now());
        event.setStatus("FIRING");
        alarmEventMapper.insert(event);

        for (ThresholdAction action : actions) {
            if ("LOG".equals(action.getActionType())) {
                ActionLog log = new ActionLog();
                log.setAlarmId(event.getId());
                log.setDeviceId(device.getId());
                log.setActionType("LOG");
                log.setLogLevel(action.getLogLevel());
                log.setMessage(String.format("设备 %s 触发规则 [%s]，当前值 %s，阈值 %s",
                        device.getName(), rule.getName(), snapshot.getMetricValue(), rule.getThresholdValue()));
                log.setCreatedAt(LocalDateTime.now());
                actionLogMapper.insert(log);
            } else if ("OPERATION".equals(action.getActionType())) {
                OpTask task = new OpTask();
                task.setDeviceId(device.getId());
                task.setOperationCode(action.getOperationCode());
                task.setParams(action.getOperationParams());
                task.setSource("AUTO");
                task.setSourceRefId(event.getId());
                task.setStatus("PENDING");
                task.setMaxRetry(3);
                task.setScheduledAt(LocalDateTime.now());
                opTaskMapper.insert(task);
            }
        }
    }

    private void resolveAlarmIfExists(ThresholdRule rule, Device device) {
        // 简化实现：将同一规则同一设备的 FIRING 告警标记为 RESOLVED
    }
}
```

- [ ] **步骤 3：创建 OpTaskExecuteJob 和 OpTaskRunner**

```java
package bob.myxos.collector.execute;

import bob.myxos.common.enums.OpTaskStatus;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.OpTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpTaskExecuteJob {
    private final OpTaskMapper opTaskMapper;
    private final OpTaskRunnerFactory runnerFactory;

    @Resource(name = "opTaskExecutor")
    private ThreadPoolExecutor opTaskExecutor;

    @Scheduled(fixedDelay = 2000)
    public void execute() {
        List<OpTask> tasks = opTaskMapper.selectList(
                new LambdaQueryWrapper<OpTask>()
                        .eq(OpTask::getStatus, OpTaskStatus.PENDING.name())
                        .le(OpTask::getScheduledAt, LocalDateTime.now())
                        .last("LIMIT 50"));
        for (OpTask task : tasks) {
            int rows = opTaskMapper.claimPending(task.getId());
            if (rows <= 0) continue;
            opTaskExecutor.execute(runnerFactory.create(task));
        }
    }
}
```

```java
package bob.myxos.collector.execute;

import bob.myxos.common.enums.OpTaskStatus;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpTaskRunnerFactory {
    private final OpTaskMapper opTaskMapper;
    private final DeviceMapper deviceMapper;
    private final MytosClientFactory clientFactory;

    public Runnable create(OpTask task) {
        return () -> {
            Device device = deviceMapper.selectById(task.getDeviceId());
            if (device == null) {
                fail(task, "设备不存在");
                return;
            }
            MytosClient client = clientFactory.create(device.getIp(), device.getPort());
            try {
                executeByCode(client, device, task);
                task.setStatus(OpTaskStatus.SUCCESS.name());
                task.setResultMsg("执行成功");
            } catch (Exception e) {
                log.error("任务执行失败 {}", task.getId(), e);
                if (task.getRetryCount() >= task.getMaxRetry()) {
                    task.setStatus(OpTaskStatus.FAILED.name());
                    task.setResultMsg(e.getMessage());
                } else {
                    task.setStatus(OpTaskStatus.PENDING.name());
                    task.setRetryCount(task.getRetryCount() + 1);
                    task.setScheduledAt(LocalDateTime.now().plusSeconds(10 * task.getRetryCount()));
                }
            } finally {
                task.setFinishedAt(LocalDateTime.now());
                opTaskMapper.updateById(task);
            }
        };
    }

    private void executeByCode(MytosClient client, Device device, OpTask task) throws Exception {
        String code = task.getOperationCode();
        String ip = device.getIp();
        String name = extractName(task.getParams());
        switch (code) {
            case "REBOOT_HOST":
                client.rebootHost(ip);
                break;
            case "RUN_ANDROID":
                client.runAndroid(ip, name);
                break;
            case "STOP_ANDROID":
                client.stopAndroid(ip, name);
                break;
            case "REBOOT_ANDROID":
                client.rebootAndroid(ip, name);
                break;
            case "RESET_ANDROID":
                client.resetAndroid(ip, name);
                break;
            case "RENAME_ANDROID":
                String newName = extractNewName(task.getParams());
                client.renameAndroid(ip, name, newName);
                break;
            case "SET_CLIPBOARD":
                client.clipboardSet(ip, name, extractText(task.getParams()));
                break;
            case "GET_CLIPBOARD":
                client.clipboardGet(ip, name);
                break;
            case "SET_LANGUAGE":
                client.setLanguage(ip, name, extractCountry(task.getParams()), extractLanguage(task.getParams()));
                break;
            case "REFRESH_LOCATION":
                client.setIpLocation(ip, name, extractLanguage(task.getParams()));
                break;
            case "SHELL_ADB":
                client.shell(ip, name, extractCommand(task.getParams()));
                break;
            default:
                throw new BizException("不支持的操作类型：" + code);
        }
    }

    private String extractName(String params) {
        // 从 JSON 参数中提取 name 字段
        return params;
    }

    private String extractNewName(String params) {
        return params;
    }

    private String extractText(String params) {
        return params;
    }

    private String extractCountry(String params) {
        return params;
    }

    private String extractLanguage(String params) {
        return params;
    }

    private String extractCommand(String params) {
        return params;
    }

    private void fail(OpTask task, String msg) {
        task.setStatus(OpTaskStatus.FAILED.name());
        task.setResultMsg(msg);
        task.setFinishedAt(LocalDateTime.now());
        opTaskMapper.updateById(task);
    }
}
```

> 说明：`SCREENSHOT` 为即时查询类操作，不进入 `op_task` 异步队列，由前端直接调用 `MytosClient.screenshot()` 临时展示。

- [ ] **步骤 4：在 Mapper 中新增 claimPending 方法**

```java
package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.OpTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface OpTaskMapper extends BaseMapper<OpTask> {

    @Update("UPDATE op_task SET status = 'RUNNING', started_at = NOW(), who_modified = 'collector', when_modified = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING' AND deleted = 0")
    int claimPending(@Param("id") Long id);
}
```

- [ ] **步骤 5：提交**

```bash
git add myxos-collector-service/src/main/java/bob/myxos/collector/evaluate/ \
        myxos-collector-service/src/main/java/bob/myxos/collector/execute/ \
        myxos-collector-service/src/main/java/bob/myxos/collector/internal/ \
        myxos-domain/src/main/java/bob/myxos/domain/mapper/OpTaskMapper.java
git commit -m "feat(collector): implement threshold evaluation and action execution pipeline"
```

---

### 任务 12：实现阈值与告警管理 API

**文件：**
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/ThresholdController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/ThresholdService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/ThresholdServiceImpl.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/ThresholdRuleReq.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/AlarmController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/AlarmService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/AlarmServiceImpl.java`

- [ ] **步骤 1：创建 ThresholdRuleReq DTO**

```java
package bob.myxos.main.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ThresholdRuleReq {
    private String name;
    private String metricType;
    private String compareOp;
    private BigDecimal thresholdValue;
    private String triggerMode;
    private Integer durationSec;
    private Integer consecutiveCount;
    private String scopeType;
    private Long scopeId;
    private List<ThresholdActionReq> actions;

    @Data
    public static class ThresholdActionReq {
        private String actionType;
        private String logLevel;
        private String operationCode;
        private String operationParams;
        private Integer sort;
    }
}
```

- [ ] **步骤 2：创建 ThresholdService 及实现**

```java
package bob.myxos.main.service;

import bob.myxos.main.dto.ThresholdRuleReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import bob.myxos.domain.entity.ThresholdRule;

public interface ThresholdService extends IService<ThresholdRule> {
    ThresholdRule create(ThresholdRuleReq req);
    ThresholdRule update(Long id, ThresholdRuleReq req);
    void toggle(Long id);
}
```

```java
package bob.myxos.main.service.impl;

import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.ThresholdService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ThresholdServiceImpl extends ServiceImpl<ThresholdRuleMapper, ThresholdRule>
        implements ThresholdService {
    private final ThresholdActionMapper actionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdRule create(ThresholdRuleReq req) {
        ThresholdRule rule = new ThresholdRule();
        rule.setName(req.getName());
        rule.setMetricType(req.getMetricType());
        rule.setCompareOp(req.getCompareOp());
        rule.setThresholdValue(req.getThresholdValue());
        rule.setDurationSec(req.getDurationSec());
        rule.setScopeType(req.getScopeType());
        rule.setScopeId(req.getScopeId());
        rule.setEnabled(1);
        baseMapper.insert(rule);
        saveActions(rule.getId(), req.getActions());
        return rule;
    }

    private void saveActions(Long ruleId, java.util.List<ThresholdRuleReq.ThresholdActionReq> actions) {
        if (actions == null) return;
        for (ThresholdRuleReq.ThresholdActionReq a : actions) {
            ThresholdAction action = new ThresholdAction();
            action.setRuleId(ruleId);
            action.setActionType(a.getActionType());
            action.setLogLevel(a.getLogLevel());
            action.setOperationCode(a.getOperationCode());
            action.setOperationParams(a.getOperationParams());
            action.setSort(a.getSort());
            actionMapper.insert(action);
        }
    }

    @Override
    public ThresholdRule update(Long id, ThresholdRuleReq req) {
        // 查询、更新 rule，删除旧 actions，重新插入
        return null;
    }

    @Override
    public void toggle(Long id) {
        ThresholdRule rule = baseMapper.selectById(id);
        rule.setEnabled(rule.getEnabled() == 1 ? 0 : 1);
        baseMapper.updateById(rule);
    }
}
```

- [ ] **步骤 3：创建 ThresholdController**

```java
package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.ThresholdService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/thresholds")
@RequiredArgsConstructor
public class ThresholdController {
    private final ThresholdService thresholdService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<ThresholdRule> create(@Valid @RequestBody ThresholdRuleReq req) {
        return Result.ok(thresholdService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<ThresholdRule> update(@PathVariable Long id, @Valid @RequestBody ThresholdRuleReq req) {
        return Result.ok(thresholdService.update(id, req));
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> toggle(@PathVariable Long id) {
        thresholdService.toggle(id);
        return Result.ok();
    }
}
```

- [ ] **步骤 4：创建 AlarmController**

```java
package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.main.service.AlarmService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {
    private final AlarmService alarmService;

    @GetMapping
    public Result<PageResult<AlarmEvent>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<AlarmEvent> p = alarmService.list(status, deviceId, page, size);
        PageResult<AlarmEvent> r = new PageResult<>();
        r.setTotal(p.getTotal());
        r.setPages(p.getPages());
        r.setCurrent(p.getCurrent());
        r.setSize(p.getSize());
        r.setRecords(p.getRecords());
        return Result.ok(r);
    }
}
```

- [ ] **步骤 5：提交**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/controller/ThresholdController.java \
        myxos-main-service/src/main/java/bob/myxos/main/controller/AlarmController.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/ThresholdService.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/AlarmService.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/impl/ThresholdServiceImpl.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/impl/AlarmServiceImpl.java \
        myxos-main-service/src/main/java/bob/myxos/main/dto/ThresholdRuleReq.java
git commit -m "feat(api): add threshold rule and alarm management APIs"
```

---

### 任务 13：实现设备发现（托管）

**文件：**
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/DiscoverController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/DiscoverService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/DiscoverServiceImpl.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/DiscoverReq.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryScanner.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryJob.java`

- [ ] **步骤 1：创建 DiscoverReq 和 DiscoverService**

```java
package bob.myxos.main.dto;

import lombok.Data;

@Data
public class DiscoverReq {
    private String cidr;
    private Integer portFrom;
    private Integer portTo;
}
```

```java
package bob.myxos.main.service;

import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.main.dto.DiscoverReq;

public interface DiscoverService {
    DiscoverTask submit(DiscoverReq req);
}
```

```java
package bob.myxos.main.service.impl;

import bob.myxos.common.util.IpUtils;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import bob.myxos.main.dto.DiscoverReq;
import bob.myxos.main.service.DiscoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscoverServiceImpl implements DiscoverService {
    private final DiscoverTaskMapper discoverTaskMapper;

    @Override
    public DiscoverTask submit(DiscoverReq req) {
        List<String> ips = IpUtils.expandCidr(req.getCidr());
        DiscoverTask task = new DiscoverTask();
        task.setCidr(req.getCidr());
        task.setPortFrom(req.getPortFrom());
        task.setPortTo(req.getPortTo());
        task.setStatus("PENDING");
        task.setScheduledAt(LocalDateTime.now());
        discoverTaskMapper.insert(task);
        return task;
    }
}
```

- [ ] **步骤 2：创建 DiscoverController**

```java
package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.main.dto.DiscoverReq;
import bob.myxos.main.service.DiscoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/discover")
@RequiredArgsConstructor
public class DiscoverController {
    private final DiscoverService discoverService;

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<DiscoverTask> scan(@Valid @RequestBody DiscoverReq req) {
        return Result.ok(discoverService.submit(req));
    }
}
```

- [ ] **步骤 3：创建 DeviceDiscoveryScanner**

```java
package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceMode;
import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.HealthResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDiscoveryScanner {
    private final MytosClientFactory clientFactory;
    private final DeviceMapper deviceMapper;
    private final DiscoverTaskMapper discoverTaskMapper;

    public void scan(DiscoverTask task) {
        List<String> ips;
        try {
            ips = bob.myxos.common.util.IpUtils.expandCidr(task.getCidr());
        } catch (Exception e) {
            failTask(task, e.getMessage());
            return;
        }
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        discoverTaskMapper.updateById(task);

        ExecutorService executor = Executors.newFixedThreadPool(16);
        AtomicInteger found = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(ips.size() * (task.getPortTo() - task.getPortFrom() + 1));

        for (String ip : ips) {
            for (int port = task.getPortFrom(); port <= task.getPortTo(); port++) {
                final int p = port;
                executor.execute(() -> {
                    try {
                        MytosClient client = clientFactory.create(ip, p);
                        HealthResp resp = client.healthcheck(ip);
                        if (resp.getCode() != null && resp.getCode() == 200) {
                            saveDiscoveredDevice(ip, p, resp);
                            found.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        task.setStatus("DONE");
        task.setFinishedAt(LocalDateTime.now());
        task.setFoundCount(found.get());
        discoverTaskMapper.updateById(task);
    }

    private void saveDiscoveredDevice(String ip, int port, HealthResp resp) {
        Long count = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getIp, ip)
                        .eq(Device::getPort, port)
                        .eq(Device::getDeleted, 0));
        if (count > 0) return;
        Device device = new Device();
        device.setName(resp.getData() != null && resp.getData().getHostIp() != null
                ? resp.getData().getHostIp()
                : ip + ":" + port);
        device.setIp(ip);
        device.setPort(port);
        device.setMode(DeviceMode.BRIDGE.name());
        device.setStatus(DeviceStatus.UNKNOWN.name());
        device.setSource("DISCOVERED");
        deviceMapper.insert(device);
    }

    private void failTask(DiscoverTask task, String message) {
        task.setStatus("FAILED");
        task.setMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        discoverTaskMapper.updateById(task);
    }
}
```

- [ ] **步骤 4：创建 DeviceDiscoveryJob**

```java
package bob.myxos.collector.collector;

import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDiscoveryJob {
    private final DiscoverTaskMapper discoverTaskMapper;
    private final DeviceDiscoveryScanner scanner;

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        List<DiscoverTask> tasks = discoverTaskMapper.selectList(
                new LambdaQueryWrapper<DiscoverTask>()
                        .eq(DiscoverTask::getStatus, "PENDING")
                        .last("LIMIT 1"));
        for (DiscoverTask task : tasks) {
            try {
                scanner.scan(task);
            } catch (Exception e) {
                log.error("网段扫描任务失败 {}", task.getId(), e);
            }
        }
    }
}
```

- [ ] **步骤 5：提交**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/controller/DiscoverController.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/DiscoverService.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/impl/DiscoverServiceImpl.java \
        myxos-main-service/src/main/java/bob/myxos/main/dto/DiscoverReq.java \
        myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryScanner.java \
        myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryJob.java
git commit -m "feat(discovery): implement CIDR device discovery and manual scan API"
```

---

### 任务 14：实现数据定时清理

**文件：**
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/cleanup/DataCleanupJob.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/SysConfigController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/SysConfigService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/SysConfigServiceImpl.java`

- [ ] **步骤 1：创建 DataCleanupJob**

```java
package bob.myxos.collector.cleanup;

import bob.myxos.domain.entity.SysConfig;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import bob.myxos.domain.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupJob {
    private final SysConfigMapper sysConfigMapper;
    private final MetricSnapshotMapper metricSnapshotMapper;
    private final ActionLogMapper actionLogMapper;
    private final AlarmEventMapper alarmEventMapper;

    @Scheduled(cron = "${myxos.cleanup.cron:0 0 3 * * ?}")
    public void cleanup() {
        log.info("开始执行数据清理任务");
        int metricDays = Integer.parseInt(getConfig("metric.retention.days", "7"));
        int logDays = Integer.parseInt(getConfig("log.retention.days", "30"));
        int alarmDays = Integer.parseInt(getConfig("alarm.retention.days", "90"));

        LocalDateTime metricDeadline = LocalDateTime.now().minusDays(metricDays);
        LocalDateTime logDeadline = LocalDateTime.now().minusDays(logDays);
        LocalDateTime alarmDeadline = LocalDateTime.now().minusDays(alarmDays);

        metricSnapshotMapper.deleteByCollectedAtBefore(metricDeadline);
        actionLogMapper.deleteByCreatedAtBefore(logDeadline);
        alarmEventMapper.deleteByFiredAtBefore(alarmDeadline);
        log.info("数据清理完成");
    }

    private String getConfig(String key, String defaultValue) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key)
                        .eq(SysConfig::getDeleted, 0));
        return config == null ? defaultValue : config.getConfigValue();
    }
}
```

- [ ] **步骤 2：在 Mapper 中新增清理方法**

```java
package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.MetricSnapshot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;

public interface MetricSnapshotMapper extends BaseMapper<MetricSnapshot> {
    @Delete("DELETE FROM metric_snapshot WHERE collected_at < #{deadline} AND deleted = 0 LIMIT 5000")
    int deleteByCollectedAtBefore(@Param("deadline") LocalDateTime deadline);
}
```

```java
package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.ActionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;

public interface ActionLogMapper extends BaseMapper<ActionLog> {
    @Delete("DELETE FROM action_log WHERE created_at < #{deadline} AND deleted = 0 LIMIT 5000")
    int deleteByCreatedAtBefore(@Param("deadline") LocalDateTime deadline);
}
```

```java
package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.AlarmEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;

public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {
    @Delete("DELETE FROM alarm_event WHERE fired_at < #{deadline} AND deleted = 0 LIMIT 5000")
    int deleteByFiredAtBefore(@Param("deadline") LocalDateTime deadline);
}
```

- [ ] **步骤 3：创建 SysConfigController**

```java
package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.SysConfig;
import bob.myxos.main.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys-config")
@RequiredArgsConstructor
public class SysConfigController {
    private final SysConfigService sysConfigService;

    @GetMapping
    public Result<List<SysConfig>> list() {
        return Result.ok(sysConfigService.list());
    }

    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable String key, @RequestParam String value) {
        sysConfigService.updateValue(key, value);
        return Result.ok();
    }
}
```

- [ ] **步骤 4：提交**

```bash
git add myxos-collector-service/src/main/java/bob/myxos/collector/cleanup/DataCleanupJob.java \
        myxos-main-service/src/main/java/bob/myxos/main/controller/SysConfigController.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/SysConfigService.java \
        myxos-main-service/src/main/java/bob/myxos/main/service/impl/SysConfigServiceImpl.java \
        myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricSnapshotMapper.java \
        myxos-domain/src/main/java/bob/myxos/domain/mapper/ActionLogMapper.java \
        myxos-domain/src/main/java/bob/myxos/domain/mapper/AlarmEventMapper.java
git commit -m "feat(cleanup): add scheduled data cleanup and system config APIs"
```

---

### 任务 15：实现前端页面（Vue 3 + Element Plus）

**文件：**
- 创建：`F:\java workspace\MYXOS\myxos-ui\` 目录下完整 Vue 3 项目
- 修改：`myxos-main-service/pom.xml` 增加 frontend-maven-plugin

- [ ] **步骤 1：在根目录初始化 Vue 项目**

```bash
cd "F:\java workspace\MYXOS"
npm create vue@3 myxos-ui
cd myxos-ui
npm install element-plus axios pinia vue-router echarts
```

- [ ] **步骤 2：创建登录页示例**

```vue
<!-- myxos-ui/src/views/LoginView.vue -->
<template>
  <div class="login-page">
    <el-form :model="form" label-width="80px">
      <el-form-item label="用户名">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" />
      </el-form-item>
      <el-button type="primary" @click="login">登录</el-button>
    </el-form>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const form = reactive({ username: '', password: '' })
const router = useRouter()

async function login() {
  const res = await axios.post('/api/auth/login', form)
  if (res.data.code === 200) {
    localStorage.setItem('token', res.data.data.token)
    axios.defaults.headers.common['Authorization'] = 'Bearer ' + res.data.data.token
    ElMessage.success('登录成功')
    router.push('/devices')
  } else {
    ElMessage.error(res.data.msg)
  }
}
</script>
```

- [ ] **步骤 3：在 main-service POM 中加入前端构建插件**

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>1.12.1</version>
    <configuration>
        <nodeVersion>v18.17.0</nodeVersion>
        <npmVersion>9.6.7</npmVersion>
        <workingDirectory>${project.basedir}/../myxos-ui</workingDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install node and npm</id>
            <goals>
                <goal>install-node-and-npm</goal>
            </goals>
        </execution>
        <execution>
            <id>npm install</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <configuration>
                <arguments>install</arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm run build</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <version>3.3.1</version>
    <executions>
        <execution>
            <id>copy-frontend-dist</id>
            <phase>prepare-package</phase>
            <goals>
                <goal>copy-resources</goal>
            </goals>
            <configuration>
                <outputDirectory>${project.basedir}/target/classes/static</outputDirectory>
                <resources>
                    <resource>
                        <directory>${project.basedir}/../myxos-ui/dist</directory>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **步骤 4：实现页面清单**

按需求实现以下页面：
- `LoginView.vue`：登录
- `DashboardView.vue`：总览
- `DeviceListView.vue`：设备状态列表
- `DeviceDetailView.vue`：设备详情与手动操作
- `ThresholdListView.vue` 和 `ThresholdEditView.vue`：阈值规则与动作配置
- `HostingView.vue`：设备托管（网段发现 + 手动添加）
- `AlarmListView.vue`：告警列表
- `LogListView.vue`：日志查询
- `SettingsView.vue`：系统配置
- `UserListView.vue`：用户管理（ADMIN 可见）

- [ ] **步骤 5：提交**

```bash
git add myxos-ui/ myxos-main-service/pom.xml
git commit -m "feat(ui): add Vue 3 frontend with Element Plus and Maven build integration"
```

---

### 任务 16：测试、部署与文档

**文件：**
- 创建：`F:\java workspace\MYXOS\docker-compose.yml`
- 创建：`F:\java workspace\MYXOS\myxos-main-service\Dockerfile`
- 创建：`F:\java workspace\MYXOS\myxos-collector-service\Dockerfile`
- 修改：`F:\java workspace\MYXOS\README.md`

- [ ] **步骤 1：编写单元测试**

为以下类编写 JUnit 5 单元测试：
- `IpUtilsTest`：验证 CIDR 展开
- `JwtTokenProviderTest`：验证签名、解析、过期
- `ThresholdEvaluatorTest`：验证比较运算和告警触发
- `MytosClientTest`：使用 OkHttp MockWebServer 验证设备 API 调用

- [ ] **步骤 2：编写集成测试**

使用 `@SpringBootTest` + Testcontainers MySQL 或 H2（MySQL 模式）测试：
- 登录 → 访问受保护接口
- 设备 CRUD
- 采集任务写入 metric_snapshot
- 阈值触发告警和 op_task

- [ ] **步骤 3：配置 JaCoCo 覆盖率**

在父 POM 中加入：

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.10</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **步骤 4：创建 Dockerfile**

```dockerfile
# myxos-main-service/Dockerfile
FROM eclipse-temurin:8-jre
COPY target/myxos-main-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xms512m", "-Xmx1g", "-XX:+UseG1GC", "-jar", "/app.jar"]
```

```dockerfile
# myxos-collector-service/Dockerfile
FROM eclipse-temurin:8-jre
COPY target/myxos-collector-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Xms512m", "-Xmx1g", "-XX:+UseG1GC", "-jar", "/app.jar"]
```

- [ ] **步骤 5：创建 docker-compose.yml**

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: myxos
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  main-service:
    build: ./myxos-main-service
    ports:
      - "8080:8080"
    environment:
      MYSQL_HOST: mysql
      MYSQL_USER: root
      MYSQL_PASSWORD: root
      MYXOS_JWT_SECRET: "请替换为至少64字节的随机字符串"
      MYXOS_INTERNAL_TOKEN: "请替换为随机内部令牌"
    depends_on:
      - mysql

  collector-service:
    build: ./myxos-collector-service
    ports:
      - "8081:8081"
    environment:
      MYSQL_HOST: mysql
      MYSQL_USER: root
      MYSQL_PASSWORD: root
      MYXOS_INTERNAL_TOKEN: "请替换为随机内部令牌"
    depends_on:
      - mysql

volumes:
  mysql_data:
```

- [ ] **步骤 6：编写 README.md**

README 必须包含：
- 项目简介
- 技术栈
- 环境要求（JDK 1.8、Maven 3.6+、Node.js 18+、MySQL 8.0）
- 本地启动步骤
- 环境变量说明（MYXOS_JWT_SECRET、MYXOS_INTERNAL_TOKEN、MYSQL_HOST 等）
- 默认账号：`admin` / `admin123`
- Docker Compose 启动方式
- 模块说明

- [ ] **步骤 7：全量构建验证**

```bash
cd "F:\java workspace\MYXOS"
mvn clean package -DskipTests
```

期望：所有模块构建成功，`myxos-main-service/target/` 和 `myxos-collector-service/target/` 下生成可执行 JAR。

- [ ] **步骤 8：提交**

```bash
git add README.md docker-compose.yml myxos-main-service/Dockerfile myxos-collector-service/Dockerfile pom.xml
git commit -m "docs(deploy): add README, Docker support and coverage config"
```

---

## 四、验收标准

- [ ] `mvn clean package` 在 JDK 1.8 环境下全量构建成功。
- [ ] `myxos-main-service` 在 8080 端口启动，`myxos-collector-service` 在 8081 端口启动。
- [ ] 可通过 UI 登录并获取 JWT；匿名访问受保护接口返回 401。
- [ ] 支持手动添加设备和 CIDR 网段发现。
- [ ] 设备状态列表支持分页、分组筛选、关键字搜索和下钻查看详情。
- [ ] 采集服务按配置间隔采集设备信息并写入 `metric_snapshot`。
- [ ] 配置阈值规则后，触发条件时自动生成告警、记录日志并执行操作。
- [ ] 设备详情页可手动下发操作并查看任务执行结果。
- [ ] 数据清理任务按配置周期删除过期数据。
- [ ] 所有数据库表记录均包含 `who_created`、`when_created`、`who_modified`、`when_modified` 字段。
- [ ] 核心服务和判定逻辑单元测试覆盖率不低于 80%。

---

## 五、执行方式选择

**计划已完成并保存到 `F:\java workspace\MYXOS\docs\superpowers\plans\2026-07-29-mytos-monitor.md`。**

请选择执行方式：

1. **子代理驱动（推荐）**：每个任务派一个新子代理执行，我负责在任务间审查并推进。
2. **本会话内联执行**：在当前会话中按任务批量执行，关键节点暂停供你检查。

建议采用 **子代理驱动** 方式，因为本计划包含 16 个大型任务，跨多个模块和语言（Java + SQL + Vue），适合并行/分阶段由专门代理处理。

