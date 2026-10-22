package com.salas.musicwave.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.salas.musicwave")
@EnableScheduling
public class MusicWaveApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicWaveApiApplication.class, args);
    }
}
