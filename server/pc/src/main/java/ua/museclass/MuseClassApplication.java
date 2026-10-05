package ua.museclass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MuseClassApplication {
    public static void main(String[] args) {
        SpringApplication.run(MuseClassApplication.class, args);
    }
}
