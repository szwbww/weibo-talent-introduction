package com.weibo.talentintroduction.auth.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.service.AuthService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ConditionalOnProperty("talent-introduction.auth.enabled", havingValue = "true", matchIfMissing = true)
class AuthWebConfig(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper
) : WebMvcConfigurer {

    @Bean
    fun authInterceptor(): AuthInterceptor {
        return AuthInterceptor(authService, objectMapper)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor())
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/login", "/api/auth/me")
    }
}
