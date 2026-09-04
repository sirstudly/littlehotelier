package com.macbackpackers.ronbot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Holds one Spring child context per hostel property so Cloudbeds cookies and DB options
 * stay isolated (one profile = one txn DB).
 * <p>
 * Not a {@code @Component} — registered only by the parent HTTP process so child
 * property contexts never nest another registry. A JVM-wide guard also ignores nested
 * {@code start()} if a child context still somehow creates this bean.
 */
public class PropertyContextRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger( PropertyContextRegistry.class );

    /** Only one registry may start property children in this JVM. */
    private static final AtomicBoolean START_CLAIMED = new AtomicBoolean( false );

    public static final Set<String> PROPERTIES = Set.of( "crh", "hsh", "rmb", "lsh" );

    @Value( "${ronbot.properties:crh,hsh,rmb,lsh}" )
    private String propertiesCsv;

    private final Map<String, ConfigurableApplicationContext> contexts = new LinkedHashMap<>();

    private boolean owner;

    @PostConstruct
    public void start() {
        if ( !START_CLAIMED.compareAndSet( false, true ) ) {
            LOGGER.warn( "Ignoring nested PropertyContextRegistry.start() — parent already owns property contexts" );
            return;
        }
        owner = true;
        try {
            for ( String property : propertiesCsv.split( "," ) ) {
                String key = property.trim().toLowerCase();
                if ( key.isEmpty() ) {
                    continue;
                }
                if ( !PROPERTIES.contains( key ) ) {
                    throw new IllegalStateException( "Unknown ronbot property: " + key );
                }
                LOGGER.info( "Starting ronbot property context for {}", key );
                ConfigurableApplicationContext child = new SpringApplicationBuilder( RonbotPropertyBootstrap.class )
                        .web( WebApplicationType.NONE )
                        .profiles( key, "ronbot-child" )
                        .properties(
                                // Must stay a single profile name — LittleHotelierConfig loads
                                // application-${spring.profiles.active}.properties
                                "spring.profiles.active=" + key,
                                "spring.main.web-application-type=none",
                                "spring.main.banner-mode=off",
                                "spring.main.lazy-initialization=false",
                                // HtmlUnit-only reads — skip WebDriverManager / Chrome download
                                "chrome.for.testing.enabled=false",
                                // Small pools: 4 properties × (txn + shared) must stay well under MySQL max_connections
                                "db.poolsize.min=1",
                                "db.poolsize.max=2",
                                "shareddb.poolsize.min=0",
                                "shareddb.poolsize.max=1",
                                "db.connection.timeout.ms=15000" )
                        .run();
                contexts.put( key, child );
                LOGGER.info( "Property context ready: {}", key );
            }
            if ( contexts.isEmpty() ) {
                throw new IllegalStateException( "No ronbot property contexts started" );
            }
        }
        catch ( RuntimeException ex ) {
            LOGGER.error( "Failed while starting property contexts; closing any that started", ex );
            stop();
            throw ex;
        }
    }

    @PreDestroy
    public void stop() {
        if ( !owner ) {
            return;
        }
        for ( Map.Entry<String, ConfigurableApplicationContext> entry : contexts.entrySet() ) {
            try {
                LOGGER.info( "Closing property context {}", entry.getKey() );
                entry.getValue().close();
            }
            catch ( Exception ex ) {
                LOGGER.warn( "Error closing property context {}", entry.getKey(), ex );
            }
        }
        contexts.clear();
        START_CLAIMED.set( false );
        owner = false;
    }

    public ConfigurableApplicationContext require( String property ) {
        String key = property == null ? "" : property.trim().toLowerCase();
        ConfigurableApplicationContext ctx = contexts.get( key );
        if ( ctx == null ) {
            throw new IllegalArgumentException( "Unknown or unavailable property: " + property
                    + ". Available: " + contexts.keySet() );
        }
        return ctx;
    }

    public CloudbedsScraper scraper( String property ) {
        return require( property ).getBean( CloudbedsScraper.class );
    }

    public WordPressDAO dao( String property ) {
        return require( property ).getBean( WordPressDAO.class );
    }

    public Set<String> availableProperties() {
        return contexts.keySet();
    }
}
