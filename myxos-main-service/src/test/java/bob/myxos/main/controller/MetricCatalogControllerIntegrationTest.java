package bob.myxos.main.controller;

import bob.myxos.main.config.SecurityConfig;
import bob.myxos.main.config.TestSecurityConfig;
import bob.myxos.main.service.MetricTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricCatalogController.class)
@ContextConfiguration(classes = {SecurityConfig.class, TestSecurityConfig.class, MetricCatalogController.class})
class MetricCatalogControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private MetricTemplateService metricTemplateService;

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void 非管理员查询指标目录应返回403() throws Exception {
        mockMvc.perform(get("/api/metric-catalogs"))
                .andExpect(status().isForbidden());
    }
}
