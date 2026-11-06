package dev.enterprisepulse.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.AppProperties;
import dev.enterprisepulse.identity.AuthService;
import dev.enterprisepulse.identity.PermissionResolver;
import dev.enterprisepulse.identity.TokenService;
import dev.enterprisepulse.data.Db;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain api(HttpSecurity http, TokenService tokens, AppProperties props, ObjectMapper json, Db db,
                                   AuthService auth, PermissionResolver permissions, TransactionTemplate tx) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> c.configurationSource(cors(props)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/forgot-password",
                                "/api/auth/reset-password", "/api/health", "/ws/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(tokenResolver())
                        .jwt(j -> j.decoder(tokens.decoder()))
                        .authenticationEntryPoint((req, res, e) -> ErrorHandler.write(res, json, 401, "Oturum açmanız gerekiyor", null))
                        .accessDeniedHandler((req, res, e) -> ErrorHandler.write(res, json, 403, "Bu işlem için yetkiniz yok", null)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> ErrorHandler.write(res, json, 401, "Oturum açmanız gerekiyor", null)))
                .addFilterBefore(new RateLimitFilter(props, json), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new RequestContextFilter(db, auth, permissions, tx, json), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private static BearerTokenResolver tokenResolver() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) -> {
            String token = header.resolve(request);
            if (token == null && request.getRequestURI().startsWith("/ws")) token = request.getParameter("access_token");
            return token;
        };
    }

    private static UrlBasedCorsConfigurationSource cors(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    static Map<String, Object> body(int status, String message, String requestId) {
        return Map.of("statusCode", status, "message", message, "requestId", requestId == null ? "" : requestId);
    }
}
