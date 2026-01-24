package work.chenhan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
