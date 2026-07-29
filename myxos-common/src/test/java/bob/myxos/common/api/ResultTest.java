package bob.myxos.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应单元测试
 */
class ResultTest {

    @Test
    void ok带数据应返回200与数据() {
        // Arrange
        String payload = "hello";

        // Act
        Result<String> r = Result.ok(payload);

        // Assert
        assertEquals(200, r.getCode());
        assertEquals("ok", r.getMsg());
        assertEquals("hello", r.getData());
    }

    @Test
    void ok无参应返回200且数据为null() {
        // Act
        Result<Void> r = Result.ok();

        // Assert
        assertEquals(200, r.getCode());
        assertEquals("ok", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    void fail带消息应返回500() {
        // Act
        Result<Void> r = Result.fail("出错了");

        // Assert
        assertEquals(500, r.getCode());
        assertEquals("出错了", r.getMsg());
        assertNull(r.getData());
    }

    @Test
    void fail带自定义code应使用该code() {
        // Act
        Result<Void> r = Result.fail(401, "未授权");

        // Assert
        assertEquals(401, r.getCode());
        assertEquals("未授权", r.getMsg());
    }
}
