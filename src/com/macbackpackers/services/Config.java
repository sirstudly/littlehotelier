package com.macbackpackers.services;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import com.macbackpackers.exceptions.UnrecoverableFault;

/**
 * Singleton for accessing configuration properties.
 */
public class Config {

    private Properties props;
    private static Config INSTANCE;

    private Config() {
        props = new Properties();
        FileReader fr = null;
        try {
            fr = new FileReader( "config.properties" );
            props.load( fr );
        } catch ( FileNotFoundException e ) {
            throw new UnrecoverableFault( e );
        } catch ( IOException e ) {
            throw new UnrecoverableFault( e );
        } finally {
            try {
                fr.close();
            } catch ( IOException e ) { /* can't do much else */
            }
        }
    }

    public static String getProperty( String key ) {
        if ( INSTANCE == null ) {
            INSTANCE = new Config();
        }
        return INSTANCE.props.getProperty( key );
    }

    public static Config getInstance() {
        if ( INSTANCE == null ) {
            INSTANCE = new Config();
        }
        return INSTANCE;
    }
}
