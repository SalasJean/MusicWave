package com.salas.musicwave.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.salas.musicwave")
@EnableScheduling
public class MusicWaveWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicWaveWorkerApplication.class, args);
    }
}
