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
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/error").permitAll() // 允许登录页和静态资源访问
                        .requestMatchers("/callback").permitAll() // Webhook 回调地址
                        .anyRequest().authenticated() // 保护所有其他管理页面
                )
                .httpBasic(Customizer.withDefaults()) // 启用 HTTP Basic 认证
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()); // 使用自定义登录页

        return http.build();
    }
}
