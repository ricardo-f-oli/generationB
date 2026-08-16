package com.generationb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulithic
@EnableScheduling
@EnableCaching
public class GenerationBApplication {
    public static void main(String[] args) {
        SpringApplication.run(GenerationBApplication.class, args);
    }
}
