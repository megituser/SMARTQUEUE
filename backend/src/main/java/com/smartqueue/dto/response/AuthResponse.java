package com.smartqueue.dto.response;

import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserResponse user;

    @Data
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UserResponse {
        private Long id;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private Long branchId;
        private String branchName;
    }
}
