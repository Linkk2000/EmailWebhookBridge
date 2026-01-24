package work.chenhan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
@EnableCaching
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
