
package com.macbackpackers.config;

import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;

@Configuration
@ComponentScan( "com.macbackpackers" )
@Import( DatabaseConfig.class )
@PropertySource( "classpath:config.properties" )
public class LittleHotelierConfig {

    @Bean( name = "reportsSQL" )
    public PropertiesFactoryBean getSqlReports() {
        PropertiesFactoryBean bean = new PropertiesFactoryBean();
        bean.setLocation( new ClassPathResource( "report_sql.xml" ) );
        return bean;
    }

    @Bean
    @Scope( "prototype" )
    public WebClient getWebClient() {
        return new WebClient( BrowserVersion.CHROME ); // return a new instance of this when requested        
    }

}
