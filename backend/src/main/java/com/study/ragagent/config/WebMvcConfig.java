package com.study.ragagent.config;

import com.study.ragagent.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册认证拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",   // 登录接口开放
                        "/api/health",       // 健康检查开放
                        "/**/*.css",         // 静态资源
                        "/**/*.js",
                        "/**/*.html",
                        "/**/*.ico",
                        "/**/*.png",
                        "/**/*.jpg",
                        "/**/*.jpeg",
                        "/**/*.gif",
                        "/**/*.svg",
                        "/**/*.woff",
                        "/**/*.woff2",
                        "/**/*.ttf",
                        "/static/**",
                        "/webjars/**"
                );
    }
}