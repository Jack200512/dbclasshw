package com.dbclasshw.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {
    private final UserMapper mapper;
    private final PasswordEncoder encoder;

    public UserService(UserMapper mapper, PasswordEncoder encoder) {
        this.mapper = mapper;
        this.encoder = encoder;
    }

    @Transactional
    public UserProfile register(UserRequests.Register request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        UserAccount user = new UserAccount();
        user.username = request.username();
        user.email = request.email().toLowerCase(Locale.ROOT);
        user.passwordHash = encoder.encode(request.password());
        mapper.insert(user);
        return me(user.id);
    }

    public UserProfile me(long id) {
        UserAccount user = mapper.findById(id);
        if (user == null || !user.enabled) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid session");
        }
        return UserProfile.from(user);
    }

    @Transactional
    public UserProfile update(long id, UserRequests.Update request) {
        me(id);
        mapper.updateEmail(id, request.email().toLowerCase(Locale.ROOT));
        return me(id);
    }
}
