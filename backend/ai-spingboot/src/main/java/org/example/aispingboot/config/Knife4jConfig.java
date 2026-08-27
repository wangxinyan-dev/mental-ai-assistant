package org.example.aispingboot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("心理健康助手 API 文档")
                        .version("1.0.0")
                        .description("面向大学生群体的 AI 心理健康支持平台")
                        .contact(new Contact()
                                .name("开发者")
                                .email("dev@example.com")));
    }
}
