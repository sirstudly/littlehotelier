
package com.macbackpackers.config;

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
import org.springframework.orm.hibernate4.HibernateTransactionManager;
import org.springframework.orm.hibernate4.LocalSessionFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
    public LocalSessionFactoryBean getSessionFactory( DataSource dataSource ) {
        LocalSessionFactoryBean bean = new LocalSessionFactoryBean();
        bean.setDataSource( dataSource );
        bean.getHibernateProperties().setProperty( "hibernate.dialect", "org.hibernate.dialect.MySQL5Dialect" );
        bean.getHibernateProperties().setProperty( "hibernate.show_sql", "true" );
        bean.setPackagesToScan( "com.macbackpackers.beans", "com.macbackpackers.jobs" );
        return bean;
    }

    @Bean
    @Autowired
    public HibernateTransactionManager getTransactionManager( SessionFactory sessionFactory ) {
        return new HibernateTransactionManager( sessionFactory );
    }
}
