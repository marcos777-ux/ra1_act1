package com.makro.aadtex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** App 1: servicio desatendido de ingestión de lotes AadTex. */
@SpringBootApplication
@EnableScheduling
public class AadApplication {
    public static void main(String[] args) {
        SpringApplication.run(AadApplication.class, args);
    }
}
