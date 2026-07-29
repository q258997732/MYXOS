package bob.myxos.mytos;

import bob.myxos.mytos.dto.InfoResp;
import bob.myxos.mytos.dto.MytosBaseResp;
import bob.myxos.mytos.dto.VersionResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MytosClient 单元测试
 * 使用 OkHttp MockWebServer 模拟设备端 HTTP 服务
 */
@DisplayName("MytosClient 设备 API 客户端测试")
class MytosClientTest {

    private MockWebServer mockWebServer;
    private MytosClient mytosClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        objectMapper = new ObjectMapper();
        String baseUrl = mockWebServer.url("/").toString();
        // 去掉末尾的斜杠，便于拼接路径
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        mytosClient = new MytosClient(httpClient, objectMapper, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ==================== info() 测试 ====================

    @Test
    @DisplayName("info() 成功时应正确解析设备信息")
    void info_should_parse_device_info_when_success() throws Exception {
        // 准备：模拟设备返回成功响应
        String responseBody = "{\"code\":200,\"msg\":\"ok\",\"data\":{" +
                "\"hostIp\":\"192.168.30.1\"," +
                "\"instance\":\"8\"," +
                "\"name\":\"p738c384c1581ad24c3fcf199684f5f5_8\"," +
                "\"buildTime\":\"1766829184\"}}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        // 执行
        InfoResp resp = mytosClient.info();

        // 断言
        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo(200);
        assertThat(resp.getMsg()).isEqualTo("ok");
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getHostIp()).isEqualTo("192.168.30.1");
        assertThat(resp.getData().getInstance()).isEqualTo("8");
        assertThat(resp.getData().getName()).isEqualTo("p738c384c1581ad24c3fcf199684f5f5_8");
        assertThat(resp.getData().getBuildTime()).isEqualTo("1766829184");

        // 验证请求路径
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/info");
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    @Test
    @DisplayName("info() 设备返回失败码时应抛出 MytosException")
    void info_should_throw_exception_when_device_returns_error_code() {
        // 准备：模拟设备返回失败响应
        String responseBody = "{\"code\":202,\"reason\":\"设备内部错误\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        // 执行 + 断言
        assertThatThrownBy(() -> mytosClient.info())
                .isInstanceOf(MytosException.class)
                .hasMessageContaining("设备内部错误");
    }

    @Test
    @DisplayName("info() HTTP 状态码非 2xx 时应抛出 MytosException")
    void info_should_throw_exception_when_http_status_not_2xx() {
        // 准备：模拟设备返回 500 错误
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // 执行 + 断言
        assertThatThrownBy(() -> mytosClient.info())
                .isInstanceOf(MytosException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("info() 网络超时时应抛出 MytosException")
    void info_should_throw_exception_when_network_timeout() {
        // 准备：不 enqueue 任何响应，让请求超时
        // 使用一个短超时的 client
        OkHttpClient shortTimeoutClient = new OkHttpClient.Builder()
                .connectTimeout(100, TimeUnit.MILLISECONDS)
                .readTimeout(100, TimeUnit.MILLISECONDS)
                .build();
        String baseUrl = mockWebServer.url("/").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        MytosClient timeoutClient = new MytosClient(shortTimeoutClient, objectMapper, baseUrl);

        // 执行 + 断言
        assertThatThrownBy(timeoutClient::info)
                .isInstanceOf(MytosException.class);
    }

    // ==================== queryVersion() 测试 ====================

    @Test
    @DisplayName("queryVersion() 成功时应正确解析版本号")
    void queryVersion_should_parse_version_when_success() throws Exception {
        // 准备
        String responseBody = "{\"code\":200,\"msg\":\"3\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        // 执行
        VersionResp resp = mytosClient.queryVersion();

        // 断言
        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo(200);
        assertThat(resp.getMsg()).isEqualTo("3");

        // 验证请求路径
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/queryversion");
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    @Test
    @DisplayName("queryVersion() 设备返回失败码时应抛出 MytosException")
    void queryVersion_should_throw_exception_when_device_returns_error_code() {
        // 准备
        String responseBody = "{\"code\":202,\"reason\":\"查询失败\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        // 执行 + 断言
        assertThatThrownBy(() -> mytosClient.queryVersion())
                .isInstanceOf(MytosException.class)
                .hasMessageContaining("查询失败");
    }

    // ==================== reboot() 测试 ====================

    @Test
    @DisplayName("reboot() 成功时应返回 ok 响应")
    void reboot_should_return_ok_when_success() throws Exception {
        // 准备
        String responseBody = "{\"code\":200,\"msg\":\"ok\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        // 执行
        MytosBaseResp resp = mytosClient.reboot();

        // 断言
        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo(200);

        // 验证请求路径
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/reboot");
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    // ==================== adbOn() / adbOff() 测试 ====================

    @Test
    @DisplayName("adbOn() 应调用 /adb?cmd=2")
    void adbOn_should_call_adb_cmd2() throws Exception {
        // 准备
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"open adb root success\"}"));

        // 执行
        MytosBaseResp resp = mytosClient.adbOn();

        // 断言
        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/adb?cmd=2");
    }

    @Test
    @DisplayName("adbOff() 应调用 /adb?cmd=3")
    void adbOff_should_call_adb_cmd3() throws Exception {
        // 准备
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"close adb root success\"}"));

        // 执行
        MytosBaseResp resp = mytosClient.adbOff();

        // 断言
        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/adb?cmd=3");
    }

    // ==================== keepaliveOn() / keepaliveOff() 测试 ====================

    @Test
    @DisplayName("keepaliveOn() 应调用 /background?cmd=2&package=xxx")
    void keepaliveOn_should_call_background_cmd2() throws Exception {
        // 准备
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"添加成功\"}"));

        // 执行
        MytosBaseResp resp = mytosClient.keepaliveOn("com.ss.android.ugc.aweme");

        // 断言
        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/background?cmd=2&package=com.ss.android.ugc.aweme");
    }

    @Test
    @DisplayName("keepaliveOff() 应调用 /background?cmd=3&package=xxx")
    void keepaliveOff_should_call_background_cmd3() throws Exception {
        // 准备
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"移除成功\"}"));

        // 执行
        MytosBaseResp resp = mytosClient.keepaliveOff("com.ss.android.ugc.aweme");

        // 断言
        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/background?cmd=3&package=com.ss.android.ugc.aweme");
    }

    // ==================== setClipboard() 测试 ====================

    @Test
    @DisplayName("setClipboard() 应调用 /clipboard?cmd=2&text=xxx 并进行 URL 编码")
    void setClipboard_should_call_clipboard_cmd2_with_url_encoding() throws Exception {
        // 准备
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        // 执行
        MytosBaseResp resp = mytosClient.setClipboard("hello world");

        // 断言
        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        // 空格应被 URL 编码为 %20 或 +
        assertThat(request.getPath()).startsWith("/clipboard?cmd=2&text=");
        assertThat(request.getPath()).contains("hello");
    }

    // ==================== 未实现操作测试 ====================

    @Test
    @DisplayName("未实现的操作应抛出 MytosException")
    void unsupported_operation_should_throw_exception() {
        assertThatThrownBy(() -> mytosClient.clearProxy())
                .isInstanceOf(MytosException.class)
                .hasMessageContaining("CLEAR_PROXY");
    }
}
