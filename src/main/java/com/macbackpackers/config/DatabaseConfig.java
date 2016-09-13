
package com.macbackpackers.config;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.macbackpackers.exceptions.UnrecoverableFault;
import com.mchange.v2.c3p0.ComboPooledDataSource;

@Configuration
@EnableTransactionManagement
@PropertySource( "classpath:config.properties" )
public class DatabaseConfig {

    @Value( "${db.url}" )
    private String url;

    @Value( "${db.username}" )
    private String username;

    @Value( "${db.password}" )
    private String password;

    @Value( "${db.driverclass}" )
    private String driverClass;

    @Bean
    public static PropertySourcesPlaceholderConfigurer getPlaceHolderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean( name = "txnDataSource" )
    public DataSource getDataSource() throws PropertyVetoException {
        ComboPooledDataSource ds = new ComboPooledDataSource();
        ds.setJdbcUrl( url );
        ds.setDriverClass( driverClass );
        ds.setUser( username );
        ds.setPassword( password );
        ds.setMinPoolSize( 3 );
        ds.setMaxPoolSize( 10 );
        ds.setTestConnectionOnCheckout( true );
        ds.setPreferredTestQuery( "SELECT 1" );
        return ds;
    }

    @Bean
    @Autowired
    @Qualifier( "txnDataSource" )
    public LocalSessionFactoryBean getSessionFactory( DataSource dataSource ) throws IOException {
        LocalSessionFactoryBean bean = new LocalSessionFactoryBean();
        bean.setDataSource( dataSource );
        bean.getHibernateProperties().putAll( getHibernateProperties() );
        bean.setPackagesToScan( "com.macbackpackers.beans", "com.macbackpackers.jobs" );
        return bean;
    }

    @Bean
    @Autowired
    public HibernateTransactionManager getTransactionManager( SessionFactory sessionFactory ) {
        return new HibernateTransactionManager( sessionFactory );
    }
    
    @Bean
    public Properties getHibernateProperties() throws IOException {
        Properties props = new Properties();
        InputStream is = getClass().getClassLoader().getResourceAsStream( "hibernate.properties" );
        if ( is == null ) {
            throw new UnrecoverableFault( "Missing hibernate.properties" );
        }
        props.load( is );
        is.close();
        return props;
    }
}
