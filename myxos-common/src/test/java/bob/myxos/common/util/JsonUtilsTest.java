package bob.myxos.common.util;

import bob.myxos.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonUtils 单元测试
 * 验证对象与 JSON 字符串的互转
 */
class JsonUtilsTest {

    @Test
    void 对象转JSON应输出合法字符串() {
        // Arrange
        Map<String, Object> map = new HashMap<>();
        map.put("name", "张三");
        map.put("age", 18);

        // Act
        String json = JsonUtils.toJson(map);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"张三\""));
        assertTrue(json.contains("\"age\":18"));
    }

    @Test
    void JSON转对象应还原字段值() {
        // Arrange
        String json = "{\"name\":\"李四\",\"age\":20}";

        // Act
        Map<?, ?> map = JsonUtils.fromJson(json, Map.class);

        // Assert
        assertNotNull(map);
        assertEquals("李四", map.get("name"));
        assertEquals(20, map.get("age"));
    }

    @Test
    void null对象转JSON应返回null字符串() {
        // Act
        String json = JsonUtils.toJson(null);

        // Assert
        assertEquals("null", json);
    }

    @Test
    void 非法JSON应抛出业务异常() {
        // Arrange
        String badJson = "{not a json}";

        // Act & Assert
        assertThrows(BizException.class, () -> JsonUtils.fromJson(badJson, Map.class));
    }

    @Test
    void 空JSON字符串应抛出业务异常() {
        assertThrows(BizException.class, () -> JsonUtils.fromJson("", Map.class));
    }
}
