package com.dbclasshw.security;

import com.dbclasshw.user.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService userDetailsService(UserMapper mapper) {
        return login -> {
            var user = mapper.findByLogin(login.trim());
            if (user == null) throw new UsernameNotFoundException("Invalid credentials");
            // Stable database ID is the principal name, even after profile updates.
            return User.withUsername(user.id.toString()).password(user.passwordHash)
                    .disabled(!user.enabled).roles("USER").build();
        };
    }

    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy loginStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts,
                                                 CsrfTokenRepository csrf, ObjectMapper json,
                                                 com.dbclasshw.sync.DocumentSocketHandler sockets) throws Exception {
        http.csrf(c -> c.csrfTokenRepository(csrf))
                .securityContext(c -> c.securityContextRepository(contexts))
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/health", "/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req,res,ex) -> {
                            res.setStatus(401); res.setContentType("application/json");
                            json.writeValue(res.getOutputStream(), Map.of("code","UNAUTHENTICATED","message","Please log in"));
                        })
                        .accessDeniedHandler((req,res,ex) -> {
                            res.setStatus(403); res.setContentType("application/json");
                            json.writeValue(res.getOutputStream(), Map.of("code","FORBIDDEN","message","Permission denied or CSRF token invalid"));
                        }))
                .logout(l -> l.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                        .addLogoutHandler((req,res,auth) -> {
                            var session=req.getSession(false);
                            if(session!=null) sockets.closeSession(session.getId());
                        })
                        .logoutSuccessHandler((req,res,auth) -> {
                            res.setContentType("application/json");
                            json.writeValue(res.getOutputStream(),Map.of("message","Logged out"));
                        }));
        return http.build();
    }
}
