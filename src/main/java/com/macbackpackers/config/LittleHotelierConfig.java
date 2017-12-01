
package com.macbackpackers.config;

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
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.macbackpackers.scrapers.matchers.RoomBedMatcher;

@Configuration
@EnableTransactionManagement
@ComponentScan( "com.macbackpackers" )
@Import( DatabaseConfig.class )
@PropertySource( "classpath:config.properties" )
public class LittleHotelierConfig {

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
        WebClient webClient = new WebClient( BrowserVersion.FIREFOX_52 );
        webClient.getOptions().setTimeout( 120000 );
        webClient.getOptions().setRedirectEnabled( true );
        webClient.getOptions().setJavaScriptEnabled( false );
        webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
        webClient.getOptions().setThrowExceptionOnScriptError( false );
        webClient.getOptions().setCssEnabled( false );
        webClient.getOptions().setUseInsecureSSL( true );
        return webClient;
    }

    @Bean( name = "webClientScriptingDisabled" )
    @Scope( "prototype" )
    public WebClient getWebClientWithScriptingDisabled() {
        // try to speed things up a bit by disabling unused functionality
        WebClient webClient = new WebClient( BrowserVersion.CHROME );
        webClient.getOptions().setTimeout( 120000 );
        webClient.getOptions().setRedirectEnabled( true );
        webClient.getOptions().setJavaScriptEnabled( false );
        webClient.getOptions().setThrowExceptionOnFailingStatusCode( false );
        webClient.getOptions().setThrowExceptionOnScriptError( false );
        webClient.getOptions().setCssEnabled( false );
        webClient.getOptions().setUseInsecureSSL( true );
        return webClient;
    }

    @Bean
    public Gson getGsonSingleton() {
        // these are thread safe
        return new GsonBuilder()
                .setDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00" )
                .setPrettyPrinting()
                .create();
    }

    @Bean
    public RoomBedMatcher getRoomBedMatcher()
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return RoomBedMatcher.class.cast( Class.forName( lhBedMatcherClassName ).newInstance() );
    }

//    @Bean
//    public Scheduler getScheduler() throws SchedulerException {
//        return StdSchedulerFactory.getDefaultScheduler();
//    }

}
