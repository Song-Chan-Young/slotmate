package com.slotmate.auth;

import com.slotmate.auth.AuthDtos.LoginRequest;
import com.slotmate.auth.AuthDtos.LoginResponse;
import com.slotmate.auth.AuthDtos.LoginUser;
import com.slotmate.auth.AuthDtos.SignupRequest;
import com.slotmate.auth.AuthDtos.SignupResponse;
import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.user.Role;
import com.slotmate.user.User;
import com.slotmate.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenIssuer tokenIssuer;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    /** 회원가입은 MEMBER 로만 생성 */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.create(request.email(), passwordEncoder.encode(request.password()),
                request.displayName(), Role.MEMBER);
        return SignupResponse.from(userRepository.save(user));
    }

    /** 계정 없음·비밀번호 틀림 모두 INVALID_CREDENTIALS (명세 5.2) */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        return new LoginResponse(tokenIssuer.issue(user), "Bearer", tokenIssuer.expiresIn(), LoginUser.from(user));
    }
}
