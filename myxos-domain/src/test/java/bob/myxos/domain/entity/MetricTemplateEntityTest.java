package bob.myxos.domain.entity;

import bob.myxos.common.enums.CompareOp;
import bob.myxos.common.enums.ConditionType;
import bob.myxos.common.enums.MetricCategory;
import bob.myxos.common.enums.MetricTargetType;
import bob.myxos.common.enums.MetricValueType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指标模板领域实体测试。
 */
class MetricTemplateEntityTest {

    private static final List<Class<?>> 指标模板实体 = Arrays.asList(
            MetricCatalog.class,
            MetricTemplate.class,
            MetricTemplateItem.class,
            MetricBinding.class
    );

    @Test
    void 应定义指标目标值类型和分类枚举() {
        assertEquals(Arrays.asList(MetricTargetType.HOST, MetricTargetType.ANDROID_INSTANCE),
                Arrays.asList(MetricTargetType.values()));
        assertEquals(Arrays.asList(MetricValueType.NUMBER, MetricValueType.STRING, MetricValueType.ENUM),
                Arrays.asList(MetricValueType.values()));
        assertEquals(Arrays.asList(MetricCategory.PERFORMANCE, MetricCategory.STATUS,
                        MetricCategory.BASIC, MetricCategory.APPLICATION),
                Arrays.asList(MetricCategory.values()));
        assertTrue(Arrays.asList(CompareOp.values()).contains(CompareOp.IN));
        assertTrue(Arrays.asList(CompareOp.values()).contains(CompareOp.NOT_IN));
        assertTrue(Arrays.asList(ConditionType.values()).contains(ConditionType.ENUM));
    }

    @Test
    void 指标模板实体应映射表并包含审计与逻辑删除字段() throws NoSuchFieldException {
        for (Class<?> 实体类型 : 指标模板实体) {
            assertNotNull(实体类型.getAnnotation(TableName.class), 实体类型.getSimpleName() + " 缺少 @TableName");
            assertEquals(String.class, 实体类型.getDeclaredField("whoCreated").getType());
            assertEquals(LocalDateTime.class, 实体类型.getDeclaredField("whenCreated").getType());
            assertEquals(String.class, 实体类型.getDeclaredField("whoModified").getType());
            assertEquals(LocalDateTime.class, 实体类型.getDeclaredField("whenModified").getType());
            Field 删除标记 = 实体类型.getDeclaredField("deleted");
            assertEquals(Integer.class, 删除标记.getType());
            assertNotNull(删除标记.getAnnotation(TableLogic.class));
        }
    }

    @Test
    void 指标绑定与历史指标实体应保留新旧标识字段() throws NoSuchFieldException {
        assertEquals(String.class, MetricBinding.class.getDeclaredField("androidName").getType());
        assertEquals(String.class, MetricBinding.class.getDeclaredField("targetType").getType());
        assertEquals(String.class, MetricBinding.class.getDeclaredField("metricCode").getType());
        assertEquals(Integer.class, MetricBinding.class.getDeclaredField("intervalSec").getType());
        assertEquals(LocalDateTime.class, MetricBinding.class.getDeclaredField("nextCollectAt").getType());
        assertEquals(String.class, MetricSnapshot.class.getDeclaredField("metricType").getType());
        assertEquals(String.class, MetricSnapshot.class.getDeclaredField("metricCode").getType());
        assertEquals(String.class, MetricSnapshot.class.getDeclaredField("targetType").getType());
        assertEquals(String.class, MetricSnapshot.class.getDeclaredField("androidName").getType());
        assertEquals(String.class, ThresholdRule.class.getDeclaredField("metricType").getType());
        assertEquals(String.class, ThresholdRule.class.getDeclaredField("metricCode").getType());
    }
}
