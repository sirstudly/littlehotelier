package com.macbackpackers.ronbot;

import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.convert.support.DefaultConversionService;

import com.macbackpackers.utils.AnyByteStringToStringConverter;

/**
 * HTTP sidecar that exposes read-only Cloudbeds booking/transaction endpoints for ronbot-mcp.
 * Per-property Cloudbeds sessions live in child Spring contexts ({@link PropertyContextRegistry}).
 * <p>
 * {@link RonbotPropertyBootstrap} must not be component-scanned here — it pulls in
 * {@code LittleHotelierConfig} which requires {@code spring.profiles.active} (crh/hsh/…).
 * Children are launched explicitly by {@link PropertyContextRegistry}.
 * <p>
 * Uses {@link SpringBootConfiguration}+{@link EnableAutoConfiguration}+one
 * {@link ComponentScan} so there is no dual scan from {@code @SpringBootApplication}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration( exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
} )
@ComponentScan(
        basePackages = "com.macbackpackers.ronbot",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = RonbotPropertyBootstrap.class
        )
)
public class RunRonbotReadApi {

    private static final Logger LOGGER = LoggerFactory.getLogger( RunRonbotReadApi.class );

    /**
     * Explicit bean so child property contexts never component-scan another registry.
     */
    @Bean( destroyMethod = "stop" )
    public PropertyContextRegistry propertyContextRegistry() {
        return new PropertyContextRegistry();
    }

    public static void main( String[] args ) {
        // Same GCP Secret Manager ByteString converter as RunProcessor
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() )
                .addConverter( new AnyByteStringToStringConverter() );

        TimeZone.setDefault( TimeZone.getTimeZone( "Europe/London" ) );

        SpringApplication app = new SpringApplication( RunRonbotReadApi.class );
        app.setWebApplicationType( WebApplicationType.SERVLET );
        app.setAdditionalProfiles( "ronbot-parent" );
        app.setDefaultProperties( Map.of(
                "server.port", System.getenv().getOrDefault( "SERVER_PORT", "8080" ),
                "spring.main.banner-mode", "off" ) );

        LOGGER.info( "Starting ronbot-read-api..." );
        app.run( args );
    }
}
