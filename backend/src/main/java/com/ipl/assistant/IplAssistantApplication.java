package com.ipl.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IplAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(IplAssistantApplication.class, args);
    }
}
