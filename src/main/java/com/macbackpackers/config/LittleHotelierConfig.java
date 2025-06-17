
package com.macbackpackers.config;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import com.macbackpackers.dao.WordPressDAO;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.htmlunit.BrowserVersion;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.NicelyResynchronizingAjaxController;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.WebWindowEvent;
import org.htmlunit.WebWindowListener;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;
import com.macbackpackers.services.BasicCardMask;

@Configuration
@EnableTransactionManagement
@ComponentScan( "com.macbackpackers" )
@Import( DatabaseConfig.class )
@PropertySource("classpath:application.properties")
@PropertySource(value = "classpath:application-${spring.profiles.active}.properties")
public class LittleHotelierConfig {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

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
    public WebClient getCloudbedsWebClient() {
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
                        .forEach( p -> LOGGER.info( p.getName() + " -> "
                                + ("card_number".equals( p.getName() ) ? new BasicCardMask().replaceCardWith( p.getValue() ) : p.getValue()) ) );
                P response = super.getPage( request );
                LOGGER.debug( "RESPONSE: " + response.getWebResponse().getContentAsString() );
                return response;
            }
        };
        webClient.getOptions().setTimeout( 120000 );
        webClient.getOptions().setRedirectEnabled( true );
        webClient.getOptions().setJavaScriptEnabled( true );
        webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
        webClient.getOptions().setThrowExceptionOnScriptError( false );
        webClient.getOptions().setCssEnabled( false );
        webClient.getOptions().setUseInsecureSSL( true );
        webClient.getOptions().setFetchPolyfillEnabled( true );
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

    @Bean( name = "gsonForExternalWebService" )
    public Gson getGsonForExternalWebService() {
        // these are thread safe
        return new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }

    @Bean
    public RoomBedMatcher getRoomBedMatcher( WordPressDAO dao )
            throws InstantiationException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        return (RoomBedMatcher) Class.forName( dao.getMandatoryOption( "hbo_bedmatcher_classname" ) ).getDeclaredConstructor().newInstance();
    }

    @Bean
    public GenericObjectPool<WebDriver> getWebDriverPool( LittleHotelierWebDriverFactory driverFactory ) {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setJmxEnabled( false ); // avoid registering multiple beans with the same JMX name (GenericObjectPool); we don't use JMX anyways
        GenericObjectPool<WebDriver> objectPool = new GenericObjectPool<>( driverFactory, config );
        objectPool.setBlockWhenExhausted( true );
        objectPool.setMaxTotal( 1 ); // only keep one around for now
        return objectPool;
    }

}
