package bob.myxos.main.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * 跨域配置
 * 允许前端开发环境（如 Vite 5173 端口）跨域访问主服务 API
 */
@Configuration
public class CorsConfig {

    /**
     * 注册 CorsFilter Bean
     *
     * @return CorsFilter 实例
     */
    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(buildSource());
    }

    /**
     * 构建跨域配置源
     * 抽出此方法便于单元测试验证配置内容
     *
     * @return 跨域配置源
     */
    public UrlBasedCorsConfigurationSource buildSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带凭证（Cookie / Authorization 头）
        config.setAllowCredentials(true);
        // 允许任意来源模式（开发环境宽松，生产环境应配置具体域名）
        config.addAllowedOriginPattern("*");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许常用 HTTP 方法
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
