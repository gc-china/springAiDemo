// src/main/java/org/zerolg/aidemo2/config/WebConfig.java

package org.zerolg.aidemo2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 允许所有路径 (/**) 接受来自所有域 (*) 的 GET/POST 请求
        // 这对于本地测试非常重要，能解决 file:/// 到 http:// 的跨域问题
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
