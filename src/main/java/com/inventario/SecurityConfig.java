package com.inventario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService details(Users users) {
    return email -> {
      AppUser u =
          users
              .findByEmail(email.toLowerCase(java.util.Locale.ROOT))
              .orElseThrow(() -> new UsernameNotFoundException("Usuario inválido"));
      return User.withUsername(u.email).password(u.password).roles(u.role).build();
    };
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/", "/index.html", "/app.js", "/style.css", "/api/csrf", "/api/register")
                    .permitAll()
                    .requestMatchers("/api/users/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/login")
                    .successHandler((q, r, a) -> r.setStatus(204))
                    .failureHandler((q, r, e) -> r.sendError(401, "Credenciales incorrectas")))
        .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, r, x) -> r.sendError(401))
                    .accessDeniedHandler((q, r, x) -> r.sendError(403)))
        .build();
  }

  @Bean
  CommandLineRunner admin(
      Users users,
      PasswordEncoder encoder,
      @Value("${app.admin.email}") String email,
      @Value("${app.admin.password}") String password) {
    return args -> {
      if (!email.isBlank()
          && !password.isBlank()
          && users.findByEmail(email.toLowerCase(java.util.Locale.ROOT)).isEmpty()) {
        if (password.length() < 12)
          throw new IllegalArgumentException("ADMIN_PASSWORD requiere 12 caracteres");
        AppUser u = new AppUser();
        u.email = email.toLowerCase(java.util.Locale.ROOT);
        u.name = "Administrador";
        u.password = encoder.encode(password);
        u.role = "ADMIN";
        users.save(u);
      }
    };
  }
}
