package pers.clare.test2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import pers.clare.session.configuration.EnableSyncSession;


@EnableSyncSession
@SpringBootApplication(
        exclude = {
                DataSourceAutoConfiguration.class
        }
)
public class ApplicationTest2 {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationTest2.class, args);
    }

}
