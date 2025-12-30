// 包声明：定义当前类所属的包路径
package org.zerolg.aidemo2.config;

// 导入Spring框架相关类
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * <p>
 * 这是Spring MVC的核心配置类，负责配置Web层的各种行为
 * <p>
 * 主要功能：
 * 1. CORS跨域配置 - 解决前后端分离架构中的跨域问题
 * 2. 静态资源配置 - 配置静态文件的访问路径
 * 3. 拦截器配置 - 配置请求拦截器和过滤器
 * 4. 消息转换器配置 - 配置JSON等数据格式的转换
 * <p>
 * CORS跨域问题说明：
 * 在现代Web开发中，前端和后端通常部署在不同的域名或端口上，
 * 浏览器的同源策略会阻止跨域请求。CORS（Cross-Origin Resource Sharing）
 * 是W3C标准，允许服务器声明哪些源站有权限访问哪些资源。
 * <p>
 * 常见的跨域场景：
 * - 前端开发服务器（如localhost:3000）访问后端API（localhost:8080）
 * - 本地HTML文件（file://协议）访问HTTP服务
 * - 不同子域名之间的访问
 * - HTTP与HTTPS之间的访问
 * <p>
 * 安全考虑：
 * 当前配置允许所有域名访问，这在开发环境中很方便，
 * 但在生产环境中应该限制为具体的域名以提高安全性。
 */
@Configuration // Spring注解：标记这是一个配置类，Spring会自动扫描并加载
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置CORS跨域映射
     *
     * 这个方法配置了系统的跨域访问策略，解决前后端分离开发中的跨域问题
     *
     * 配置说明：
     * 1. 路径映射：允许所有路径（/**）接受跨域请求
     * 2. 源站模式：允许所有域名（*）访问，支持通配符模式
     * 3. HTTP方法：支持常用的HTTP方法（GET、POST、PUT、DELETE、OPTIONS）
     * 4. 请求头：允许所有请求头（*）
     * 5. 凭证支持：允许携带Cookie和认证信息
     *
     * 特别说明：
     * - allowCredentials(true)：允许请求携带Cookie、Authorization等凭证
     * - OPTIONS方法：浏览器在跨域请求前会发送预检请求（preflight request）
     * - allowedOriginPatterns：使用模式匹配，比allowedOrigins更灵活
     *
     * @param registry CORS注册器，用于注册跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置跨域映射规则
        registry.addMapping("/**") // 对所有API路径生效
                .allowedOriginPatterns("*") // 允许所有源站访问（支持通配符）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的HTTP方法
                .allowedHeaders("*") // 允许所有请求头
                .allowCredentials(true); // 允许携带凭证信息（Cookie、Authorization等）

        // 这个配置对于本地开发非常重要，能够解决以下跨域问题：
        // 1. file:/// 协议访问 http:// 服务的跨域问题
        // 2. 不同端口之间的跨域问题（如前端3000端口访问后端8080端口）
        // 3. 开发环境中前端热重载服务器的跨域问题
        // 4. 移动端应用或桌面应用访问Web API的跨域问题
    }
}
