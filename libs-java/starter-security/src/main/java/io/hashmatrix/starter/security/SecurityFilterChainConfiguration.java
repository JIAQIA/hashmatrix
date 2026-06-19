package io.hashmatrix.starter.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 默认安全过滤链：无状态、放行探针/指标、其余需认证，并前置 {@link GatewayPreAuthFilter}；
 * 同时开启方法级授权（{@link EnableMethodSecurity}）。仅 Servlet Web 应用下生效。
 *
 * <p>鉴权矩阵语义（「网关前置鉴权」通用约定）：<b>无身份（匿名）→ 401 Unauthorized</b>（由
 * {@link HttpStatusEntryPoint} 钉死，避免 Spring 默认对匿名访问受保护资源回 403）；<b>已认证但缺角色 → 403
 * Forbidden</b>（默认 {@code AccessDeniedHandler}）。401/403 语义分明，是网关后各服务的统一基线。
 *
 * <p>子仓可提供自定义 {@code SecurityFilterChain} Bean 覆盖（{@code @ConditionalOnMissingBean}）。
 */
@AutoConfiguration(
        after = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableMethodSecurity
@ConditionalOnProperty(
        prefix = "hashmatrix.security",
        name = "enabled",
        matchIfMissing = true)
public class SecurityFilterChainConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain hashmatrixSecurityFilterChain(
            HttpSecurity http, GatewayPreAuthFilter preAuthFilter, SecurityProperties properties)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        registry ->
                                registry.requestMatchers(
                                                properties.getPermitPaths().toArray(new String[0]))
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // 无身份（匿名）→ 401；已认证但缺角色 → 由默认 AccessDeniedHandler 返回 403。
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(preAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
