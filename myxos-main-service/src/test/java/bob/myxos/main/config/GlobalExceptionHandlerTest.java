package bob.myxos.main.config;

import bob.myxos.common.api.Result;
import bob.myxos.common.exception.BizException;
import bob.myxos.mytos.MytosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理器单元测试
 * 覆盖：业务异常、认证异常、授权异常、参数校验异常、未知异常
 */
@DisplayName("全局异常处理器测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("处理业务异常应返回对应状态码与消息")
    void handleBizExceptionReturnsCodeAndMsg() {
        // Arrange
        BizException ex = new BizException(1001, "业务校验失败");

        // Act
        Result<Void> result = handler.handleBizException(ex);

        // Assert
        assertNotNull(result);
        assertEquals(1001, result.getCode());
        assertEquals("业务校验失败", result.getMsg());
    }

    @Test
    @DisplayName("处理用户不存在异常应返回 401")
    void handleUsernameNotFoundReturns401() {
        // Arrange
        UsernameNotFoundException ex = new UsernameNotFoundException("用户不存在");

        // Act
        Result<Void> result = handler.handleUsernameNotFound(ex);

        // Assert
        assertNotNull(result);
        assertEquals(401, result.getCode());
    }

    @Test
    @DisplayName("处理访问拒绝异常应返回 403")
    void handleAccessDeniedReturns403() {
        // Arrange
        AccessDeniedException ex = new AccessDeniedException("无权限");

        // Act
        Result<Void> result = handler.handleAccessDenied(ex);

        // Assert
        assertNotNull(result);
        assertEquals(403, result.getCode());
    }

    @Test
    @DisplayName("处理参数校验异常应返回 400 并拼接字段错误")
    void handleValidationReturns400() throws Exception {
        // Arrange
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "username", "用户名不能为空"));
        MethodParameter mp = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, bindingResult);

        // Act
        Result<Void> result = handler.handleValidation(ex);

        // Assert
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().contains("用户名不能为空"));
    }

    @Test
    @DisplayName("处理 MYTOS 设备业务异常应返回 502 与设备错误消息")
    void handleMytosExceptionReturnsDeviceCodeAndMsg() {
        // Arrange
        MytosException ex = new MytosException(201, "设备返回错误: 截图失败");

        // Act
        Result<Void> result = handler.handleMytosException(ex);

        // Assert
        assertNotNull(result);
        assertEquals(502, result.getCode());
        assertEquals("设备返回错误: 截图失败", result.getMsg());
    }

    @Test
    @DisplayName("处理设备连接类异常应返回 500 通用提示")
    void handleMytosExceptionWithoutDeviceCodeReturns500() {
        // Arrange
        MytosException ex = new MytosException("设备 HTTP 调用异常: Connection refused: 192.168.30.2:9082");

        // Act
        Result<Void> result = handler.handleMytosException(ex);

        // Assert
        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertEquals("设备连接失败，请检查网络或设备状态", result.getMsg());
    }

    @Test
    @DisplayName("处理未知异常应返回 500 与通用提示")
    void handleExceptionReturns500() {
        // Arrange
        Exception ex = new RuntimeException("数据库连接失败");

        // Act
        Result<Void> result = handler.handleException(ex);

        // Assert
        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertNotNull(result.getMsg());
    }

    @Test
    @DisplayName("处理空消息业务异常不应抛出 NPE")
    void handleBizExceptionWithNullMsg() {
        // Arrange
        BizException ex = new BizException(500, null);

        // Act
        Result<Void> result = handler.handleBizException(ex);

        // Assert
        assertNotNull(result);
        assertEquals(500, result.getCode());
    }

    /** 用于构造 MethodParameter 的桩方法 */
    @SuppressWarnings("unused")
    private void dummyMethod(String arg) {
        // 测试桩
    }
}
