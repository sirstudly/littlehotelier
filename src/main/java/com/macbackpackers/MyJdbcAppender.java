package com.macbackpackers;

import java.sql.SQLException;

import org.apache.log4j.LogManager;
import org.apache.log4j.jdbc.JDBCAppender;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Hacky bit of code to escape quotes when using the JDBCAppender.
 *
 */
public class MyJdbcAppender extends JDBCAppender {

    @Override
    protected String getLogStatement(LoggingEvent event) {

        if( null != event.getThrowableInformation() && event.getThrowableInformation().getThrowable() instanceof SQLException ) {
            SQLException myexce = new SQLException( 
                    event.getThrowableInformation().getThrowable().getMessage().replaceAll( "'"," " ),
                    event.getThrowableInformation().getThrowable() );
            
            LoggingEvent clone = new LoggingEvent(
                    event.fqnOfCategoryClass,
                    LogManager.getLogger(event.getLoggerName()),
                    event.getLevel(),
                    event.getMessage().toString().replaceAll( "'", " " ), 
                    myexce); 
            return getLayout().format(clone);
        }

        LoggingEvent clone = new LoggingEvent(
                event.fqnOfCategoryClass,
                LogManager.getLogger(event.getLoggerName()),
                event.getLevel(),
                event.getMessage().toString().replaceAll( "'", " " ),
                event.getThrowableInformation() != null ? event.getThrowableInformation().getThrowable() : null
              );
        
        return getLayout().format(clone);
    }
}