package pers.clare.test;

import pers.clare.session.EnableSyncSession;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableSyncSession
@SpringBootApplication
public class ApplicationTest2 {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationTest2.class, args);
    }

}
