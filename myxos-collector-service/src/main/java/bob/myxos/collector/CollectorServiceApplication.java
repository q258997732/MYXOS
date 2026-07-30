package bob.myxos.collector;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"bob.myxos.collector", "bob.myxos.domain", "bob.myxos.mytos"})
@MapperScan("bob.myxos.domain.mapper")
public class CollectorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollectorServiceApplication.class, args);
    }
}
