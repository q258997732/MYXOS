package bob.myxos.mytos;

import bob.myxos.common.enums.OperationCode;
import bob.myxos.mytos.dto.HealthResp;
import bob.myxos.mytos.dto.HostVerResp;
import bob.myxos.mytos.dto.MytosBaseResp;
import bob.myxos.mytos.dto.ScreenshotResp;
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
import java.util.HashMap;
import java.util.Map;
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
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        mytosClient = new MytosClient(httpClient, objectMapper, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ==================== 健康检查测试 ====================

    @Test
    @DisplayName("healthcheck() 成功时应正确解析健康状态")
    void healthcheck_should_parse_health_status_when_success() throws Exception {
        String responseBody = "{\"code\":200,\"msg\":\"ok\",\"data\":{" +
                "\"dockerApi\":true,\"pingStatus\":true,\"hostIp\":\"192.168.30.2\"}}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        HealthResp resp = mytosClient.healthcheck("192.168.30.2");

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo(200);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getDockerApi()).isTrue();
        assertThat(resp.getData().getPingStatus()).isTrue();
        assertThat(resp.getData().getHostIp()).isEqualTo("192.168.30.2");

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/host_api/v1/healthcheck/192.168.30.2");
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("healthcheck() 设备返回失败码时应抛出 MytosException")
    void healthcheck_should_throw_exception_when_device_returns_error_code() {
        String responseBody = "{\"code\":500,\"reason\":\"设备内部错误\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        assertThatThrownBy(() -> mytosClient.healthcheck("192.168.30.2"))
                .isInstanceOf(MytosException.class)
                .hasMessageContaining("设备内部错误");
    }

    // ==================== 主机版本测试 ====================

    @Test
    @DisplayName("getHostVer() 成功时应正确解析版本号")
    void getHostVer_should_parse_version_when_success() throws Exception {
        String responseBody = "{\"code\":200,\"data\":\"QL-q1-2024.v0.2.9\",\"message\":\"success\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        HostVerResp resp = mytosClient.getHostVer("192.168.30.2");

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo(200);
        assertThat(resp.getData()).isEqualTo("QL-q1-2024.v0.2.9");
        assertThat(resp.getMsg()).isEqualTo("success");

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/dc_api/v1/get_host_ver/192.168.30.2");
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    // ==================== 容器生命周期测试 ====================

    @Test
    @DisplayName("rebootAndroid() 应调用 /dc_api/v1/reboot/{ip}/{name}")
    void rebootAndroid_should_call_correct_path() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        MytosBaseResp resp = mytosClient.rebootAndroid("192.168.30.2", "instance_01");

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/dc_api/v1/reboot/192.168.30.2/instance_01");
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    @Test
    @DisplayName("stopAndroid() 应调用 /dc_api/v1/stop/{ip}/{name}")
    void stopAndroid_should_call_correct_path() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        MytosBaseResp resp = mytosClient.stopAndroid("192.168.30.2", "instance_01");

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/dc_api/v1/stop/192.168.30.2/instance_01");
    }

    @Test
    @DisplayName("runAndroid() 应调用 /dc_api/v1/run/{ip}/{name}")
    void runAndroid_should_call_correct_path() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        MytosBaseResp resp = mytosClient.runAndroid("192.168.30.2", "instance_01");

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/dc_api/v1/run/192.168.30.2/instance_01");
    }

    @Test
    @DisplayName("resetAndroid() 应调用 /dc_api/v1/reset/{ip}/{name}")
    void resetAndroid_should_call_correct_path() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        MytosBaseResp resp = mytosClient.resetAndroid("192.168.30.2", "instance_01");

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/dc_api/v1/reset/192.168.30.2/instance_01");
    }

    @Test
    @DisplayName("renameAndroid() 应调用 /dc_api/v1/rename/{ip}/{oldName}/{newName}")
    void renameAndroid_should_call_correct_path() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        MytosBaseResp resp = mytosClient.renameAndroid("192.168.30.2", "old_name", "new_name");

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/dc_api/v1/rename/192.168.30.2/old_name/new_name");
    }

    // ==================== 手动操作面板测试 ====================

    @Test
    @DisplayName("screenshot() 应调用 /and_api/v1/screenshots/{ip}/{name}/{level}")
    void screenshot_should_call_correct_path() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"message\":\"/9j/base64data\",\"data\":{\"url\":\"http://host/snap\"}}"));

        ScreenshotResp resp = mytosClient.screenshot("192.168.30.2", "instance_01", "1");

        assertThat(resp.getCode()).isEqualTo(200);
        assertThat(resp.getMsg()).isEqualTo("/9j/base64data");
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().get("url").asText()).isEqualTo("http://host/snap");
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/and_api/v1/screenshots/192.168.30.2/instance_01/1");
    }

    @Test
    @DisplayName("clipboardSet() 应调用 /and_api/v1/clipboard_set/{ip}/{name} 并携带 text 参数")
    void clipboardSet_should_call_correct_path_with_text() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        MytosBaseResp resp = mytosClient.clipboardSet("192.168.30.2", "instance_01", "hello world");

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).startsWith("/and_api/v1/clipboard_set/192.168.30.2/instance_01");
        assertThat(request.getPath()).contains("text=hello%20world");
    }

    @Test
    @DisplayName("shell() 应调用 /and_api/v1/shell/{ip}/{name} 并 POST JSON 命令")
    void shell_should_post_json_command() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"message\":\"/\\r\\n\",\"data\":{\"shell_code\":0}}"));

        mytosClient.shell("192.168.30.2", "instance_01", "ls -l");

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/and_api/v1/shell/192.168.30.2/instance_01");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"cmd\":\"ls -l\"}");
    }

    @Test
    @DisplayName("shell() 应拒绝包含高危操作的命令")
    void shell_should_reject_dangerous_command() {
        assertThatThrownBy(() -> mytosClient.shell("192.168.30.2", "instance_01", "rm -rf /data"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("高危");
    }

    @Test
    @DisplayName("shell() 应拒绝超长命令")
    void shell_should_reject_too_long_command() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb.append('a');
        }
        String longCommand = sb.toString();
        assertThatThrownBy(() -> mytosClient.shell("192.168.30.2", "instance_01", longCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度");
    }

    // ==================== 统一执行入口测试 ====================

    @Test
    @DisplayName("execute(REBOOT_HOST) 应调用 rebootHost")
    void execute_should_call_rebootHost_for_REBOOT_HOST() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        MytosBaseResp resp = mytosClient.execute(OperationCode.REBOOT_HOST, null);

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/host_api/v1/reboot_host/127.0.0.1");
    }

    @Test
    @DisplayName("execute(REBOOT_ANDROID) 应携带 name 参数调用 rebootAndroid")
    void execute_should_call_rebootAndroid_with_name() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"msg\":\"ok\"}"));

        Map<String, Object> params = new HashMap<>();
        params.put("name", "instance_01");
        MytosBaseResp resp = mytosClient.execute(OperationCode.REBOOT_ANDROID, params);

        assertThat(resp.getCode()).isEqualTo(200);
        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/dc_api/v1/reboot/127.0.0.1/instance_01");
    }

    @Test
    @DisplayName("execute() 缺少必需参数时应抛出 IllegalArgumentException")
    void execute_should_throw_exception_when_missing_required_param() {
        assertThatThrownBy(() -> mytosClient.execute(OperationCode.REBOOT_ANDROID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("execute() 未知操作码时应抛出 MytosException")
    void execute_should_throw_exception_for_null_code() {
        assertThatThrownBy(() -> mytosClient.execute(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作码不能为空");
    }
}
