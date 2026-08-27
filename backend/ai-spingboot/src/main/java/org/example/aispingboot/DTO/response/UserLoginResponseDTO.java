package org.example.aispingboot.DTO.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class UserLoginResponseDTO {
    private String token;
    private String roleType;
    private UserDetailResponseDTO userInfo;
    @Builder
    @Data
    public static class UserDetailResponseDTO {
        private Long id;
        private String username;
        private String email;
        private String nickname;
        private String avatar;
        private String phone;
        private Integer gender;
        private String genderDisplayName;
        private LocalDate birthday;
        private Integer userType;
        private String userTypeDisplayName;
        private Integer status;
        private String statusDisplayName;
        private String displayName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
