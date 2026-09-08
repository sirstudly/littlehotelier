package com.macbackpackers;

import com.macbackpackers.utils.AnyByteStringToStringConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Lightweight Spring Boot entry used by integration tests ({@code @SpringBootTest}).
 * <p>
 * Gated with {@code @Profile("test")} so production scanners
 * ({@code RunProcessor}, {@code RonbotPropertyBootstrap}) never register this
 * configuration when they component-scan {@code com.macbackpackers}.
 * Tests must include {@code test} in {@code spring.profiles.active}
 * (e.g. {@code test,crh}).
 */
@Profile( "test" )
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
        SpringApplication app = new SpringApplication( SecretsManagerTestApp.class );
        app.setAdditionalProfiles( "test" );
        app.run( args );
    }

    @Override
    public void run( String... args ) {
        System.out.println( "db.username = " + dbUsername );
        System.out.println( "db.password = " + dbPassword );
    }
}
