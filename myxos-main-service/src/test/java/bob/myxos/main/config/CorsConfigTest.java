package bob.myxos.main.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨域配置单元测试
 */
@DisplayName("跨域配置测试")
class CorsConfigTest {

    @Test
    @DisplayName("应注册 CorsFilter Bean")
    void corsFilterBeanCreated() {
        // Arrange
        CorsConfig config = new CorsConfig();

        // Act
        CorsFilter filter = config.corsFilter();

        // Assert
        assertNotNull(filter);
    }

    @Test
    @DisplayName("CorsConfiguration 应允许凭证与常见方法")
    void corsConfigurationAllowsCredentialsAndMethods() {
        // Arrange
        CorsConfig config = new CorsConfig();

        // Act
        UrlBasedCorsConfigurationSource source = config.buildSource();
        CorsConfiguration cfg = source.getCorsConfigurations().get("/**");

        // Assert
        assertNotNull(cfg);
        assertEquals(Boolean.TRUE, cfg.getAllowCredentials());
        assertTrue(cfg.getAllowedMethods().contains("GET"));
        assertTrue(cfg.getAllowedMethods().contains("POST"));
        assertTrue(cfg.getAllowedMethods().contains("PUT"));
        assertTrue(cfg.getAllowedMethods().contains("DELETE"));
        assertTrue(cfg.getAllowedMethods().contains("OPTIONS"));
    }
}
