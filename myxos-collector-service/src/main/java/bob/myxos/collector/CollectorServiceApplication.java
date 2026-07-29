package bob.myxos.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"bob.myxos.collector", "bob.myxos.domain"})
public class CollectorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollectorServiceApplication.class, args);
    }
}
