
package com.macbackpackers.config;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Value( "${db.maxidletime}" )
    private int maxIdleTime;
    
    @Value( "${db.idle.connection.test.period}" )
    private int idleConnectionTestPeriod;

    @Value( "${db.max.idle.time.excess.connections}" )
    private int maxIdleTimeExcessConnections;

    @Value( "${db.driverclass}" )
    private String driverClass;
    
    @Bean
    public static PropertySourcesPlaceholderConfigurer getPlaceHolderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean( name = "txnDataSource" )
    public DataSource getDataSource(
            @Value( "${db.url}" ) String url,
            @Value( "${db.username}" ) String username,
            @Value( "${db.password}" ) String password,
            @Value( "${db.poolsize.min}" ) int minPoolSize,
            @Value( "${db.poolsize.max}" ) int maxPoolSize ) throws PropertyVetoException {
        return createDataSource( url, username, password, minPoolSize, maxPoolSize );
    }

    @Bean( name = "sharedDataSource" )
    public DataSource getSharedDataSource(
            @Value( "${shareddb.url}" ) String url,
            @Value( "${shareddb.username}" ) String username,
            @Value( "${shareddb.password}" ) String password,
            @Value( "${shareddb.poolsize.min}" ) int minPoolSize,
            @Value( "${shareddb.poolsize.max}" ) int maxPoolSize ) throws PropertyVetoException {
        return createDataSource( url, username, password, minPoolSize, maxPoolSize );
    }

    private DataSource createDataSource( String dbUrl, String user, String pass, int minPoolSize, int maxPoolSize ) throws PropertyVetoException {
        ComboPooledDataSource ds = new ComboPooledDataSource();
        ds.setJdbcUrl( dbUrl );
        ds.setDriverClass( driverClass );
// uncomment following to check for db connection leaks
// http://www.mchange.com/projects/c3p0/#unreturnedConnectionTimeout
//        ds.setUnreturnedConnectionTimeout( 600 ); // in seconds
//        ds.setDebugUnreturnedConnectionStackTraces( true );
        ds.setUser( user );
        ds.setPassword( pass );
        ds.setMinPoolSize( minPoolSize );
        ds.setMaxPoolSize( maxPoolSize );
        ds.setMaxIdleTime( maxIdleTime );
        ds.setIdleConnectionTestPeriod( idleConnectionTestPeriod );
        ds.setMaxIdleTimeExcessConnections( maxIdleTimeExcessConnections );
        ds.setTestConnectionOnCheckout( true );
        ds.setPreferredTestQuery( "SELECT 1" );
        return ds;
    }

    @Bean( name = "sharedJdbcTemplate" )
    public JdbcTemplate getSharedJdbcTemplate( @Qualifier( "sharedDataSource" ) DataSource dataSource ) {
        return new JdbcTemplate( dataSource );
    }

    @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    @Bean( name = "entityManagerFactory" )
    public LocalContainerEntityManagerFactoryBean getLocalContainerEntityManagerFactoryBean( @Qualifier( "txnDataSource" ) DataSource dataSource ) throws IOException {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource( dataSource );
        emf.setPackagesToScan( "com.macbackpackers.beans", "com.macbackpackers.jobs" );
        emf.setJpaVendorAdapter( new HibernateJpaVendorAdapter() );
        emf.setJpaProperties( getHibernateProperties() );
        return emf;
    }
    
    @Bean( name = "transactionManager" )
    public PlatformTransactionManager getTransactionManager( @Qualifier( "txnDataSource" ) DataSource dataSource, EntityManagerFactory emf ) {
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
