package com.dbclasshw.user;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private final UserService users;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy loginStrategy;
    private final SecurityContextRepository contexts;

    public UserController(UserService users, AuthenticationManager authenticationManager,
                          SessionAuthenticationStrategy loginStrategy, SecurityContextRepository contexts) {
        this.users=users; this.authenticationManager=authenticationManager;
        this.loginStrategy=loginStrategy; this.contexts=contexts;
    }

    @GetMapping("/api/auth/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/api/auth/register")
    public ResponseEntity<UserProfile> register(@Valid @RequestBody UserRequests.Register request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.register(request));
    }

    @PostMapping("/api/auth/login")
    public UserProfile login(@Valid @RequestBody UserRequests.Login body,
                             HttpServletRequest request, HttpServletResponse response) {
        var auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.login(),body.password()));
        loginStrategy.onAuthentication(auth,request,response);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context,request,response);
        return users.me(Long.parseLong(auth.getName()));
    }

    @GetMapping("/api/users/me")
    public UserProfile me(Authentication auth) { return users.me(Long.parseLong(auth.getName())); }

    @PutMapping("/api/users/me")
    public UserProfile update(Authentication auth, @Valid @RequestBody UserRequests.Update body) {
        return users.update(Long.parseLong(auth.getName()),body);
    }
}
