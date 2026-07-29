package bob.myxos.domain.audit;

import bob.myxos.domain.entity.SysUser;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisXMLLanguageDriver;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审计字段填充处理器单元测试
 * 验证 insertFill / updateFill 是否正确填充审计字段
 */
class AuditMetaObjectHandlerTest {

    private AuditMetaObjectHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuditMetaObjectHandler();
        // 初始化 MyBatis-Plus 的 TableInfo，使 strictInsertFill 能识别字段
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    @Test
    void insertFill_未登录时使用system填充所有审计字段() {
        // Arrange
        SysUser user = new SysUser();
        MetaObject metaObject = SystemMetaObject.forObject(user);

        // Act
        handler.insertFill(metaObject);

        // Assert
        assertEquals("system", user.getWhoCreated(), "未登录时 whoCreated 应为 system");
        assertEquals("system", user.getWhoModified(), "未登录时 whoModified 应为 system");
        assertNotNull(user.getWhenCreated(), "whenCreated 不应为空");
        assertNotNull(user.getWhenModified(), "whenModified 不应为空");
        assertEquals(0, user.getDeleted(), "deleted 应默认填充为 0");
    }

    @Test
    void insertFill_登录用户使用登录名填充() {
        // Arrange
        LoginUserHolder.set("admin");
        SysUser user = new SysUser();
        MetaObject metaObject = SystemMetaObject.forObject(user);

        // Act
        handler.insertFill(metaObject);

        // Assert
        assertEquals("admin", user.getWhoCreated());
        assertEquals("admin", user.getWhoModified());
    }

    @Test
    void insertFill_时间字段接近当前时间() {
        // Arrange
        SysUser user = new SysUser();
        MetaObject metaObject = SystemMetaObject.forObject(user);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        // Act
        handler.insertFill(metaObject);

        // Assert
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);
        assertTrue(user.getWhenCreated().isAfter(before) && user.getWhenCreated().isBefore(after),
                "whenCreated 应在当前时间附近");
        assertTrue(user.getWhenModified().isAfter(before) && user.getWhenModified().isBefore(after),
                "whenModified 应在当前时间附近");
    }

    @Test
    void updateFill_仅更新修改字段() {
        // Arrange
        LoginUserHolder.set("operator");
        SysUser user = new SysUser();
        user.setWhoCreated("creator");
        user.setWhenCreated(LocalDateTime.of(2026, 1, 1, 0, 0));
        MetaObject metaObject = SystemMetaObject.forObject(user);

        // Act
        handler.updateFill(metaObject);

        // Assert
        assertEquals("operator", user.getWhoModified(), "updateFill 应更新 whoModified");
        assertNotNull(user.getWhenModified(), "updateFill 应更新 whenModified");
        assertEquals("creator", user.getWhoCreated(), "updateFill 不应改动 whoCreated");
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), user.getWhenCreated(),
                "updateFill 不应改动 whenCreated");
    }

    @Test
    void loginUserHolder_默认返回system() {
        LoginUserHolder.clear();
        assertEquals("system", LoginUserHolder.get());
    }

    @Test
    void loginUserHolder_set与clear生效() {
        LoginUserHolder.set("tester");
        assertEquals("tester", LoginUserHolder.get());
        LoginUserHolder.clear();
        assertEquals("system", LoginUserHolder.get());
    }
}
