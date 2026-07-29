package bob.myxos.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 实体字段规范校验测试
 * 验证所有实体类都包含审计字段（whoCreated/whenCreated/whoModified/whenModified）
 * 以及逻辑删除字段（deleted），并且注解使用正确
 */
class EntityFieldCheckTest {

    /** 所有需要审计字段的实体类 */
    private static final List<Class<?>> ENTITY_CLASSES = Arrays.asList(
            SysUser.class,
            Device.class,
            DeviceGroup.class,
            MetricSnapshot.class,
            ThresholdRule.class,
            ThresholdAction.class,
            AlarmEvent.class,
            OpTask.class,
            ActionLog.class,
            DiscoverTask.class,
            SysConfig.class,
            LoginToken.class
    );

    @Test
    void 所有实体必须包含TableName注解() {
        for (Class<?> clazz : ENTITY_CLASSES) {
            TableName tableName = clazz.getAnnotation(TableName.class);
            assertNotNull(tableName, clazz.getSimpleName() + " 缺少 @TableName 注解");
            assertFalse(tableName.value().isEmpty(), clazz.getSimpleName() + " 的 @TableName 值不能为空");
        }
    }

    @Test
    void 所有实体必须包含五个审计与删除字段() {
        for (Class<?> clazz : ENTITY_CLASSES) {
            Map<String, Field> fieldMap = Arrays.stream(clazz.getDeclaredFields())
                    .collect(Collectors.toMap(Field::getName, Function.identity()));

            assertTrue(fieldMap.containsKey("whoCreated"), clazz.getSimpleName() + " 缺少 whoCreated 字段");
            assertTrue(fieldMap.containsKey("whenCreated"), clazz.getSimpleName() + " 缺少 whenCreated 字段");
            assertTrue(fieldMap.containsKey("whoModified"), clazz.getSimpleName() + " 缺少 whoModified 字段");
            assertTrue(fieldMap.containsKey("whenModified"), clazz.getSimpleName() + " 缺少 whenModified 字段");
            assertTrue(fieldMap.containsKey("deleted"), clazz.getSimpleName() + " 缺少 deleted 字段");

            assertEquals(String.class, fieldMap.get("whoCreated").getType(),
                    clazz.getSimpleName() + ".whoCreated 类型应为 String");
            assertEquals(LocalDateTime.class, fieldMap.get("whenCreated").getType(),
                    clazz.getSimpleName() + ".whenCreated 类型应为 LocalDateTime");
            assertEquals(String.class, fieldMap.get("whoModified").getType(),
                    clazz.getSimpleName() + ".whoModified 类型应为 String");
            assertEquals(LocalDateTime.class, fieldMap.get("whenModified").getType(),
                    clazz.getSimpleName() + ".whenModified 类型应为 LocalDateTime");
            assertEquals(Integer.class, fieldMap.get("deleted").getType(),
                    clazz.getSimpleName() + ".deleted 类型应为 Integer");
        }
    }

    @Test
    void 审计字段必须使用正确的FieldFill注解() {
        for (Class<?> clazz : ENTITY_CLASSES) {
            Map<String, Field> fieldMap = Arrays.stream(clazz.getDeclaredFields())
                    .collect(Collectors.toMap(Field::getName, Function.identity()));

            TableField whoCreated = fieldMap.get("whoCreated").getAnnotation(TableField.class);
            assertNotNull(whoCreated, clazz.getSimpleName() + ".whoCreated 缺少 @TableField");
            assertEquals(FieldFill.INSERT, whoCreated.fill(),
                    clazz.getSimpleName() + ".whoCreated 应使用 FieldFill.INSERT");

            TableField whenCreated = fieldMap.get("whenCreated").getAnnotation(TableField.class);
            assertNotNull(whenCreated, clazz.getSimpleName() + ".whenCreated 缺少 @TableField");
            assertEquals(FieldFill.INSERT, whenCreated.fill(),
                    clazz.getSimpleName() + ".whenCreated 应使用 FieldFill.INSERT");

            TableField whoModified = fieldMap.get("whoModified").getAnnotation(TableField.class);
            assertNotNull(whoModified, clazz.getSimpleName() + ".whoModified 缺少 @TableField");
            assertEquals(FieldFill.INSERT_UPDATE, whoModified.fill(),
                    clazz.getSimpleName() + ".whoModified 应使用 FieldFill.INSERT_UPDATE");

            TableField whenModified = fieldMap.get("whenModified").getAnnotation(TableField.class);
            assertNotNull(whenModified, clazz.getSimpleName() + ".whenModified 缺少 @TableField");
            assertEquals(FieldFill.INSERT_UPDATE, whenModified.fill(),
                    clazz.getSimpleName() + ".whenModified 应使用 FieldFill.INSERT_UPDATE");
        }
    }

    @Test
    void deleted字段必须使用TableLogic注解() {
        for (Class<?> clazz : ENTITY_CLASSES) {
            try {
                Field deleted = clazz.getDeclaredField("deleted");
                TableLogic logic = deleted.getAnnotation(TableLogic.class);
                assertNotNull(logic, clazz.getSimpleName() + ".deleted 缺少 @TableLogic 注解");
            } catch (NoSuchFieldException e) {
                fail(clazz.getSimpleName() + " 缺少 deleted 字段");
            }
        }
    }

    @Test
    void thresholdRule必须包含触发模式相关字段() {
        Map<String, Field> fieldMap = Arrays.stream(ThresholdRule.class.getDeclaredFields())
                .collect(Collectors.toMap(Field::getName, Function.identity()));
        assertTrue(fieldMap.containsKey("triggerMode"), "ThresholdRule 缺少 triggerMode 字段");
        assertTrue(fieldMap.containsKey("durationSec"), "ThresholdRule 缺少 durationSec 字段");
        assertTrue(fieldMap.containsKey("consecutiveCount"), "ThresholdRule 缺少 consecutiveCount 字段");
    }
}
