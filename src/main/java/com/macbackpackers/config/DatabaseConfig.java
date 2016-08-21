
package com.macbackpackers.config;

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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.macbackpackers.exceptions.UnrecoverableFault;

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
    public DataSource getDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl( url );
        ds.setDriverClassName( driverClass );
        ds.setUsername( username );
        ds.setPassword( password );
        return ds;
    }

    @Bean
    @Autowired
    @Qualifier( "txnDataSource" )
    public LocalSessionFactoryBean getSessionFactory( DataSource dataSource ) throws IOException {
        LocalSessionFactoryBean bean = new LocalSessionFactoryBean();
        bean.setDataSource( dataSource );
//        bean.getHibernateProperties().setProperty( "hibernate.dialect", "org.hibernate.dialect.MySQL5Dialect" );
//        bean.getHibernateProperties().setProperty( "hibernate.show_sql", "true" );
//        bean.getHibernateProperties().setProperty( "hibernate.c3p0.min_size", "3" );
//        bean.getHibernateProperties().setProperty( "hibernate.c3p0.max_size", "10" );
//        bean.getHibernateProperties().setProperty( "hibernate.c3p0.timeout", "100" ); // max number of seconds connection to remain pooled and idle (otherwise discarded)
//        bean.getHibernateProperties().setProperty( "hibernate.c3p0.max_statements", "50" ); // max number of prepared statements to cache
//        bean.getHibernateProperties().setProperty( "hibernate.c3p0.idle_test_period", "1000" ); // number of seconds to test each connection in pool
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
