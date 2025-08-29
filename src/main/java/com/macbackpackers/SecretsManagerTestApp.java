package com.macbackpackers;

import com.macbackpackers.utils.AnyByteStringToStringConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.support.DefaultConversionService;

@SpringBootApplication
public class SecretsManagerTestApp implements CommandLineRunner {

    @Value( "${db.username:NOT_FOUND}" )
    private String dbUsername;

    @Value( "${db.password:NOT_FOUND}" )
    private String dbPassword;

    public static void main( String[] args ) {
        // The problem is that most likely, autoconfiguration happens early on, before
        // GcpSecretManagerEnvironmentPostProcessor had a chance to run and introduce ByteString converters.
        // See https://stackoverflow.com/a/71226714
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() ).addConverter( new AnyByteStringToStringConverter() );
        SpringApplication.run( SecretsManagerTestApp.class, args );
    }

    @Override
    public void run( String... args ) {
        System.out.println( "db.username = " + dbUsername );
        System.out.println( "db.password = " + dbPassword );
    }
}