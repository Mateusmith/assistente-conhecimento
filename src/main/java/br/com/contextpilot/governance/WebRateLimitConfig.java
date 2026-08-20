package br.com.contextpilot.governance;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebRateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;

    WebRateLimitConfig(RateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
