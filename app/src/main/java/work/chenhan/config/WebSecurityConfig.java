package work.chenhan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 禁用 CSRF 以支持 Webhook 接收和简单的 POST 请求
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/error").permitAll() // 允许静态资源访问
                        .requestMatchers("/callback").permitAll() // Webhook 回调地址由于通常由机器触发且校验在业务层，通常设为免鉴权或使用单独的 Token
                        .anyRequest().authenticated() // 保护所有其他管理页面
                )
                .httpBasic(Customizer.withDefaults()) // 启用 HTTP Basic 认证
                .formLogin(Customizer.withDefaults()); // 同时启用表单登录，方便浏览器用户使用

        return http.build();
    }
}
