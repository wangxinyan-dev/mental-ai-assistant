package org.example.aispingboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConsultationStreamDTO {
    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    @NotBlank(message = "初始消息不能为空")
    @Size(max = 2000, message = "初始消息长度不能超过2000个字符")
    private String userMessage;
}
