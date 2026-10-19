package com.slotmate.auth;

import com.slotmate.user.Role;
import com.slotmate.user.User;
import com.slotmate.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** .env 의 ADMIN_EMAIL·ADMIN_PASSWORD 로 관리자 계정을 없을 때만 생성 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                   @Value("${slotmate.admin.email}") String email,
                                   @Value("${slotmate.admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank() || userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.create(email, passwordEncoder.encode(password), "관리자", Role.ADMIN));
        log.info("관리자 계정 생성: {}", email);
    }
}
