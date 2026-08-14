package org.example.aispingboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AiSpingbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSpingbootApplication.class, args);
    }

}
