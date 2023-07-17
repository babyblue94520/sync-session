package pers.clare.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import pers.clare.session.EnableSyncSession;

@EnableSyncSession
@SpringBootApplication
public class ApplicationTest2 {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationTest2.class, args);
    }

}
