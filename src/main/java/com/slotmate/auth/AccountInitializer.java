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

/** .env 의 ADMIN_*·MEMBER_* 값으로 관리자·테스트 회원 계정을 없을 때만 생성 */
@Component
public class AccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String memberEmail;
    private final String memberPassword;

    public AccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              @Value("${slotmate.admin.email}") String adminEmail,
                              @Value("${slotmate.admin.password}") String adminPassword,
                              @Value("${slotmate.member.email}") String memberEmail,
                              @Value("${slotmate.member.password}") String memberPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.memberEmail = memberEmail;
        this.memberPassword = memberPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        create(adminEmail, adminPassword, "관리자", Role.ADMIN);
        create(memberEmail, memberPassword, "테스트회원", Role.MEMBER);
    }

    private void create(String email, String password, String displayName, Role role) {
        if (email.isBlank() || password.isBlank() || userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(User.create(email, passwordEncoder.encode(password), displayName, role));
        log.info("{} 계정 생성: {}", role, email);
    }
}
