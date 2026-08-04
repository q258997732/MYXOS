package bob.myxos.main.controller;
import bob.myxos.main.config.SecurityConfig;
import bob.myxos.main.config.TestSecurityConfig;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.Device;
import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备管理控制器集成测试
 * 验证分页查询、创建、更新、删除接口的权限与参数校验
 */
@WebMvcTest(DeviceController.class)
@ContextConfiguration(classes = {SecurityConfig.class, TestSecurityConfig.class, DeviceController.class})
class DeviceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    @MockBean
    private DeviceService deviceService;

    @Test
    @DisplayName("未认证访问设备列表应返回 401")
    void listWithoutAuthReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("已认证用户可分页查询设备列表")
    void listWithAuthReturnsPageResult() throws Exception {
        // Arrange
        DeviceListResp resp = new DeviceListResp();
        resp.setId(1L);
        resp.setName("测试设备");
        resp.setStatus(DeviceStatus.ONLINE.name());
        resp.setAlarmCount(0L);
        Page<DeviceListResp> page = new Page<DeviceListResp>(1, 20);
        page.setRecords(Collections.singletonList(resp));
        page.setTotal(1);
        when(deviceService.listDevices(isNull(), isNull(), isNull(), anyLong(), anyLong())).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("测试设备"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("管理员可以创建设备")
    void createDeviceWithAdminReturnsCreated() throws Exception {
        // Arrange
        Device saved = new Device();
        saved.setId(1L);
        saved.setName("测试设备");
        saved.setIp("192.168.30.2");
        saved.setPort(9082);
        when(deviceService.createDevice(any(DeviceCreateReq.class))).thenReturn(saved);

        DeviceCreateReq req = new DeviceCreateReq();
        req.setName("测试设备");
        req.setIp("192.168.30.2");
        req.setPort(9082);
        req.setMode("BRIDGE");

        // Act & Assert
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试设备"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("创建设备参数不合法应返回 400")
    void createDeviceWithInvalidBodyReturnsBadRequest() throws Exception {
        DeviceCreateReq req = new DeviceCreateReq();
        req.setName("");
        req.setIp("invalid-ip");
        req.setPort(99999);
        req.setMode("UNKNOWN");

        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    @DisplayName("操作员可以更新设备")
    void updateDeviceWithOperatorReturnsOk() throws Exception {
        Device updated = new Device();
        updated.setId(1L);
        updated.setName("新名称");
        when(deviceService.updateDevice(anyLong(), any())).thenReturn(updated);

        mockMvc.perform(put("/api/devices/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名称"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    @DisplayName("VIEWER 角色删除设备应返回 403")
    void deleteDeviceWithViewerReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/devices/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("执行 Adb 命令应以 JSON body 提交并返回输出")
    void shellWithJsonBodyReturnsOutput() throws Exception {
        // Arrange
        when(deviceService.executeShell(anyLong(), any(String.class), any(String.class)))
                .thenReturn("package:com.example.app");

        // Act & Assert：含空格的命令放在 JSON 字段中，不会被防火墙当作表单参数名拦截
        mockMvc.perform(post("/api/devices/1/shell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"c1\",\"command\":\"pm list packages\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value("package:com.example.app"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("执行 Adb 命令缺少命令字段应返回 400")
    void shellWithoutCommandReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/devices/1/shell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"c1\"}"))
                .andExpect(status().isBadRequest());
    }
}
