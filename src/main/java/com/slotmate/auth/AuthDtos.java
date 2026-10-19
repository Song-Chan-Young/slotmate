package com.slotmate.auth;

import com.slotmate.user.Role;
import com.slotmate.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API 명세 5절 인증 요청·응답. 길이 상한은 users 컬럼 길이 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotNull @Size(min = 8, max = 64) String password) {
    }

    public record SignupResponse(Long userId, String displayName, String email, Role role) {
        static SignupResponse from(User user) {
            return new SignupResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole());
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, LoginUser user) {
    }

    public record LoginUser(Long userId, String displayName, Role role) {
        static LoginUser from(User user) {
            return new LoginUser(user.getId(), user.getDisplayName(), user.getRole());
        }
    }
}
