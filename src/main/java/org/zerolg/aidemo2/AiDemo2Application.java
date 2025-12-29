package org.zerolg.aidemo2;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
@MapperScan(basePackages = {"org.zerolg.aidemo2.mapper", "org.zerolg.aidemo2.audit.mapper"})
public class AiDemo2Application {

    public static void main(String[] args) {
        SpringApplication.run(AiDemo2Application.class, args);
    }

}
