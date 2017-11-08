
package com.macbackpackers.config;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.macbackpackers.exceptions.UnrecoverableFault;
import com.mchange.v2.c3p0.ComboPooledDataSource;

@Configuration
@EnableTransactionManagement
@PropertySource( "classpath:config.properties" )
public class DatabaseConfig {

    @Value( "${processor.thread.count:1}" )
    private int threadCount;

    @Value( "${db.poolsize.min}" )
    private int minPoolSize;

    @Value( "${db.poolsize.max}" )
    private int maxPoolSize;

    @Value( "${db.maxidletime}" )
    private int maxIdleTime;
    
    @Value( "${db.idle.connection.test.period}" )
    private int idleConnectionTestPeriod;

    @Value( "${db.max.idle.time.excess.connections}" )
    private int maxIdleTimeExcessConnections;

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
// uncomment following to check for db connection leaks
// http://www.mchange.com/projects/c3p0/#unreturnedConnectionTimeout
//        ds.setUnreturnedConnectionTimeout( 600 ); // in seconds
//        ds.setDebugUnreturnedConnectionStackTraces( true );
        ds.setUser( username );
        ds.setPassword( password );
        ds.setMinPoolSize( minPoolSize );
        ds.setMaxPoolSize( maxPoolSize );
        ds.setMaxIdleTime( maxIdleTime );
        ds.setIdleConnectionTestPeriod( idleConnectionTestPeriod );
        ds.setMaxIdleTimeExcessConnections( maxIdleTimeExcessConnections );
        ds.setTestConnectionOnCheckout( true );
        ds.setPreferredTestQuery( "SELECT 1" );
        return ds;
    }

    @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    @Bean( name = "entityManagerFactory" )
    @Autowired
    @Qualifier( "txnDataSource" )
    public LocalContainerEntityManagerFactoryBean getLocalContainerEntityManagerFactoryBean( DataSource dataSource ) throws IOException {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource( dataSource );
        emf.setPackagesToScan( "com.macbackpackers.beans", "com.macbackpackers.jobs" );
        emf.setJpaVendorAdapter( new HibernateJpaVendorAdapter() );
        emf.setJpaProperties( getHibernateProperties() );
        return emf;
    }
    
    @Bean( name = "transactionManager" )
    @Autowired
    @Qualifier( "txnDataSource" )
    public PlatformTransactionManager getTransactionManager( DataSource dataSource, EntityManagerFactory emf ) {
        JpaTransactionManager tm = new JpaTransactionManager();
        tm.setEntityManagerFactory( emf );
        tm.setDataSource( dataSource );
        tm.setDefaultTimeout( 60 );
        return tm;
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
