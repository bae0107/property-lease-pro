package com.jugu.propertylease.main;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("local") // 只在local profile 激活时生效
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 放行所有路径
        registry.addMapping("/**")
                // 允许所有来源（如果是 SpringBoot 2.4 以下用 allowedOrigins("*")）
                .allowedOriginPatterns("*") 
                // 允许的方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 允许的请求头
                .allowedHeaders("*")
                // 是否允许携带 cookie
                .allowCredentials(true)
                // 跨域允许时间
                .maxAge(3600);
    }
}