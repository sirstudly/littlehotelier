package com.macbackpackers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
 
@Configuration
@ComponentScan("com.macbackpackers")
@Import( DatabaseConfig.class )
@PropertySource("classpath:config.properties")
public class LittleHotelierConfig {
 
//    @Bean(name="helloWorldBean")
//    @Description("This is a sample HelloWorld Bean")
//    public HelloWorld helloWorld() {
//        return new HelloWorldImpl();
//    }
 
}
