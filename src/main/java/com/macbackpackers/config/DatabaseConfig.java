
package com.macbackpackers.config;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.macbackpackers.exceptions.UnrecoverableFault;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    @Value( "${db.maxidletime}" )
    private int maxIdleTime;

    @Value( "${db.idle.connection.test.period}" )
    private int idleConnectionTestPeriod;

    @Value( "${db.max.idle.time.excess.connections}" )
    private int maxIdleTimeExcessConnections;

    @Value( "${db.driverclass}" )
    private String driverClass;

    @Bean( name = "txnDataSource" )
    public DataSource getDataSource(
            @Value( "${db.url}" ) String url,
            @Value( "${db.username}" ) String username,
            @Value( "${db.password}" ) String password,
            @Value( "${db.poolsize.min}" ) int minPoolSize,
            @Value( "${db.poolsize.max}" ) int maxPoolSize,
            @Value( "${db.connection.timeout.ms:30000}" ) long connectionTimeoutMs ) throws PropertyVetoException {
        return createDataSource( url, username, password, minPoolSize, maxPoolSize, connectionTimeoutMs );
    }

    @Bean( name = "sharedDataSource" )
    public DataSource getSharedDataSource(
            @Value( "${shareddb.url}" ) String url,
            @Value( "${shareddb.username}" ) String username,
            @Value( "${shareddb.password}" ) String password,
            @Value( "${shareddb.poolsize.min}" ) int minPoolSize,
            @Value( "${shareddb.poolsize.max}" ) int maxPoolSize,
            @Value( "${db.connection.timeout.ms:30000}" ) long connectionTimeoutMs ) throws PropertyVetoException {
        return createDataSource( url, username, password, minPoolSize, maxPoolSize, connectionTimeoutMs );
    }

    private DataSource createDataSource( String dbUrl, String user, String pass, int minPoolSize, int maxPoolSize,
            long connectionTimeoutMs ) throws PropertyVetoException {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName( driverClass );
        config.setJdbcUrl( withTcpKeepAlive( dbUrl ) );
        config.setUsername( user );
        config.setPassword( pass );
        config.setMaximumPoolSize( maxPoolSize );
        config.setMinimumIdle( minPoolSize );
        config.setConnectionTimeout( connectionTimeoutMs );
        // Recycle connections before remote MySQL wait_timeout (or Tailscale) kills them.
        // db.maxidletime=0 historically meant "never expire" under c3p0; use 10 minutes instead.
        long maxLifetimeMs = maxIdleTime > 0 ? maxIdleTime * 1000L : 600_000L;
        config.setMaxLifetime( maxLifetimeMs );
        // Hikari keepalive minimum is 30s; map unused c3p0 idleConnectionTestPeriod intent.
        long keepaliveMs = Math.max( 30_000L, idleConnectionTestPeriod * 1000L );
        config.setKeepaliveTime( keepaliveMs );
        if ( maxIdleTimeExcessConnections > 0 ) {
            config.setIdleTimeout( maxIdleTimeExcessConnections * 1000L );
        }
        config.setConnectionTestQuery( "SELECT 1" );
        return new HikariDataSource( config );
    }

    /** Ensures MySQL JDBC URLs request TCP keepalive. */
    private static String withTcpKeepAlive( String dbUrl ) {
        if ( dbUrl == null || dbUrl.contains( "tcpKeepAlive=" ) ) {
            return dbUrl;
        }
        return dbUrl + ( dbUrl.contains( "?" ) ? "&" : "?" ) + "tcpKeepAlive=true";
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
