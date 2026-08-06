package bob.myxos.main.controller;

import bob.myxos.main.config.SecurityConfig;
import bob.myxos.main.config.TestSecurityConfig;
import bob.myxos.main.dto.MetricTemplateReq;
import bob.myxos.main.service.MetricTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricTemplateController.class)
@ContextConfiguration(classes = {SecurityConfig.class, TestSecurityConfig.class, MetricTemplateController.class})
class MetricTemplateControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private MetricTemplateService metricTemplateService;

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void 非管理员创建模板应返回403() throws Exception {
        mockMvc.perform(post("/api/metric-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"模板\",\"targetType\":\"HOST\",\"items\":[{\"metricCatalogId\":1,\"defaultIntervalSec\":60}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void 非管理员查询模板列表应返回403() throws Exception {
        mockMvc.perform(get("/api/metric-templates"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void 非管理员查询模板详情应返回403() throws Exception {
        mockMvc.perform(get("/api/metric-templates/1"))
                .andExpect(status().isForbidden());
    }
}
