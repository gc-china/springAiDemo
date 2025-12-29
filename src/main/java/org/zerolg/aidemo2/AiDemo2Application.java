package org.zerolg.aidemo2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class AiDemo2Application {

    public static void main(String[] args) {
        SpringApplication.run(AiDemo2Application.class, args);
    }

}
