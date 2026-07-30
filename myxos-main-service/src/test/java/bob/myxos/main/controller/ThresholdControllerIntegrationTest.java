package bob.myxos.main.controller;
import bob.myxos.main.config.SecurityConfig;
import bob.myxos.main.config.TestSecurityConfig;

import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.ThresholdService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阈值规则控制器集成测试
 * 验证规则 CRUD、权限控制与参数校验
 */
@WebMvcTest(ThresholdController.class)
@ContextConfiguration(classes = {SecurityConfig.class, TestSecurityConfig.class, ThresholdController.class})
class ThresholdControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    @MockBean
    private ThresholdService thresholdService;

    @Test
    @DisplayName("未认证访问阈值规则列表应返回 401")
    void listWithoutAuthReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/thresholds"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("已认证用户可查询阈值规则分页")
    void listWithAuthReturnsPageResult() throws Exception {
        ThresholdRule rule = new ThresholdRule();
        rule.setId(1L);
        rule.setName("CPU 高负载");
        rule.setMetricType("CPU");
        rule.setEnabled(1);
        Page<ThresholdRule> page = new Page<ThresholdRule>(1, 20);
        page.setRecords(Collections.singletonList(rule));
        page.setTotal(1);
        when(thresholdService.list(any(), any(), anyLong(), anyLong())).thenReturn(page);

        mockMvc.perform(get("/api/thresholds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("CPU 高负载"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("管理员可创建阈值规则")
    void createThresholdWithAdminReturnsOk() throws Exception {
        ThresholdRule saved = new ThresholdRule();
        saved.setId(1L);
        saved.setName("CPU 高负载");
        saved.setMetricType("CPU");
        saved.setCompareOp("GT");
        saved.setThresholdValue(new BigDecimal("80"));
        when(thresholdService.create(any(ThresholdRuleReq.class))).thenReturn(saved);

        ThresholdRuleReq req = buildValidReq();

        mockMvc.perform(post("/api/thresholds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("CPU 高负载"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("创建阈值规则参数不合法应返回 400")
    void createThresholdWithInvalidBodyReturnsBadRequest() throws Exception {
        ThresholdRuleReq req = new ThresholdRuleReq();
        req.setName("");
        req.setMetricType("CPU");
        req.setCompareOp("INVALID");
        req.setThresholdValue(new BigDecimal("-1"));
        req.setTriggerMode("UNKNOWN");
        req.setScopeType("ALL");

        mockMvc.perform(post("/api/thresholds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    @DisplayName("操作员可更新阈值规则")
    void updateThresholdWithOperatorReturnsOk() throws Exception {
        ThresholdRule updated = new ThresholdRule();
        updated.setId(1L);
        updated.setName("CPU 高负载更新");
        when(thresholdService.update(anyLong(), any(ThresholdRuleReq.class))).thenReturn(updated);

        ThresholdRuleReq req = buildValidReq();
        req.setName("CPU 高负载更新");

        mockMvc.perform(put("/api/thresholds/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("CPU 高负载更新"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    @DisplayName("VIEWER 删除阈值规则应返回 403")
    void deleteThresholdWithViewerReturnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/thresholds/1"))
                .andExpect(status().isForbidden());
    }

    private ThresholdRuleReq buildValidReq() {
        ThresholdRuleReq req = new ThresholdRuleReq();
        req.setName("CPU 高负载");
        req.setMetricType("CPU");
        req.setCompareOp("GT");
        req.setThresholdValue(new BigDecimal("80"));
        req.setTriggerMode("DURATION");
        req.setDurationSec(60);
        req.setScopeType("ALL");
        return req;
    }
}
