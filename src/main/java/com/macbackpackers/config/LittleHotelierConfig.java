
package com.macbackpackers.config;

import java.io.IOException;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebWindowEvent;
import com.gargoylesoftware.htmlunit.WebWindowListener;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

@Configuration
@EnableTransactionManagement
@ComponentScan( "com.macbackpackers" )
@Import( DatabaseConfig.class )
@PropertySource( "classpath:config.properties" )
public class LittleHotelierConfig {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${lilhotelier.bedmatcher.classname}" )
    private String lhBedMatcherClassName;

    @Bean( name = "reportsSQL" )
    public PropertiesFactoryBean getSqlReports() {
        PropertiesFactoryBean bean = new PropertiesFactoryBean();
        bean.setLocation( new ClassPathResource( "report_sql.xml" ) );
        return bean;
    }

    @Bean( name = "webClient" )
    @Scope( "prototype" )
    public WebClient getWebClient() {
        WebClient webClient = new WebClient( BrowserVersion.CHROME ); // return a new instance of this when requested
        webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
        webClient.getOptions().setThrowExceptionOnScriptError( false );
        webClient.getOptions().setJavaScriptEnabled( true );
        webClient.getOptions().setCssEnabled( false );
        webClient.getOptions().setRedirectEnabled( true );
        webClient.getOptions().setUseInsecureSSL( true );
        webClient.setAjaxController( new NicelyResynchronizingAjaxController() );
        webClient.getOptions().setTimeout( 60000 );
        webClient.setJavaScriptTimeout( 60000 );
        return webClient;
    }

    @Bean( name = "webClientForBDC" )
    @Scope( "prototype" )
    public WebClient getWebClientForBDC() {
        WebClient webClient = new WebClient( BrowserVersion.FIREFOX );
        webClient.getOptions().setTimeout( 120000 );
        webClient.getOptions().setRedirectEnabled( true );
        webClient.getOptions().setJavaScriptEnabled( true );
        webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
        webClient.getOptions().setThrowExceptionOnScriptError( false );
        webClient.getOptions().setCssEnabled( true );
        webClient.getOptions().setUseInsecureSSL( true );
        webClient.setAjaxController( new NicelyResynchronizingAjaxController() );
        webClient.addWebWindowListener( new WebWindowListener() {
            @Override
            public void webWindowOpened( WebWindowEvent event ) {
            }

            @Override
            public void webWindowContentChanged( WebWindowEvent event ) {
                LOGGER.info( "Content changed: " + event.getNewPage().getUrl() );
            }

            @Override
            public void webWindowClosed( WebWindowEvent event ) {
            }
        });
        return webClient;
    }

    @Bean( name = "webClientForHostelworldLogin" )
    @Scope( "prototype" )
    public WebClient getWebClientForHostelworldLogin() {
        // return the default web client (with JS enabled)
        WebClient webClient = getWebClientForHostelworld();
        webClient.getOptions().setJavaScriptEnabled( true );
        return webClient;
    }

    @Bean( name = "webClientForHostelworld" )
    @Scope( "prototype" )
    public WebClient getWebClientForHostelworld() {
        WebClient webClient = new WebClient( BrowserVersion.FIREFOX );
        webClient.getOptions().setTimeout( 120000 );
        webClient.getOptions().setRedirectEnabled( true );
        webClient.getOptions().setJavaScriptEnabled( false );
        webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
        webClient.getOptions().setThrowExceptionOnScriptError( false );
        webClient.getOptions().setCssEnabled( false );
        webClient.getOptions().setUseInsecureSSL( true );
        return webClient;
    }

    @Bean( name = "webClientForCloudbeds" )
    @Scope( "prototype" )
    public WebClient getCloudbedsWebClient() throws IOException {
        WebClient webClient = new WebClient( BrowserVersion.CHROME ) {
            private static final long serialVersionUID = 3571378018703618188L;
            @Override
            public <P extends Page> P getPage( final WebRequest request ) throws IOException,
                    FailingHttpStatusCodeException {
                LOGGER.info( request.getHttpMethod() + ": " + request.getUrl() );
                if ( request.getRequestBody() != null ) {
                    LOGGER.info( request.getRequestBody() );
                }
                request.getRequestParameters().stream()
                        .forEach( p -> LOGGER.info( p.getName() + " -> " + p.getValue() ) );
                return super.getPage( request );
            }
        };
        webClient.getOptions().setTimeout( 120000 );
        webClient.getOptions().setRedirectEnabled( true );
        webClient.getOptions().setJavaScriptEnabled( false );
        webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
        webClient.getOptions().setThrowExceptionOnScriptError( false );
        webClient.getOptions().setCssEnabled( false );
        webClient.getOptions().setUseInsecureSSL( true );
        return webClient;
    }

    @Bean( name = "gsonForLH" )
    public Gson getGsonForLittleHotelier() {
        // these are thread safe
        return new GsonBuilder()
                .setDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00" )
                .setPrettyPrinting()
                .create();
    }

    @Bean( name = "gsonForCloudbeds" )
    public Gson getGsonForCloudbeds() {
        // these are thread safe
        return new GsonBuilder()
                .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES )
                .setPrettyPrinting()
                .create();
    }

    @Bean( name = "gsonForCloudbedsIdentity" )
    public Gson getGsonForCloudbedsIdentity() {
        return new GsonBuilder()
                .setFieldNamingPolicy( FieldNamingPolicy.IDENTITY )
                .setPrettyPrinting()
                .create();
    }

    @Bean( name = "gsonForHostelworld" )
    public Gson getGsonForHostelworld() {
        // these are thread safe
        return new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    @Bean( name = "gsonForSagepay" )
    public Gson getGsonForSagepay() {
        // these are thread safe
        return new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    @Bean
    public RoomBedMatcher getRoomBedMatcher()
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return RoomBedMatcher.class.cast( Class.forName( lhBedMatcherClassName ).newInstance() );
    }

    @Bean
    public GenericObjectPool<WebDriver> getWebDriverPool( LittleHotelierWebDriverFactory driverFactory ) {
        GenericObjectPool<WebDriver> objectPool = new GenericObjectPool<WebDriver>( driverFactory );
        objectPool.setBlockWhenExhausted( true );
        objectPool.setMaxTotal( 1 ); // only keep one around for now
        return objectPool;
    }

}
