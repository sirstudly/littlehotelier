package com.macbackpackers.utils;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.stream.Stream;

public class MDCUtilsTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( MDCUtilsTest.class );

    @Test
    public void testWrapWithMDC() {
        MDC.put( "traceId", "12345" );
        LOGGER.info( "testWrapWithMDC {}", MDC.get( "traceId" ) );
        var mdcContext = MDC.getCopyOfContextMap();
        try {
            Stream.of( 1, 2, 3, 4, 5, 6, 7, 8 ).parallel()
                    .map( MDCUtils.wrapWithMDC( i -> {
                        LOGGER.info( Thread.currentThread().getName() + " " + i + ": " + MDC.get( "traceId" ) );
                        return i * 2;
                    } ) )
                    .forEach( i -> LOGGER.info( Thread.currentThread().getName() + " DONE " + i ) );
        }
        finally {
            MDC.setContextMap( mdcContext );
        }
        LOGGER.info( "testWrapWithMDC FINISHED: {}", MDC.get( "traceId" ) );
        assert MDC.get( "traceId" ) != null; // ensure we don't lose the MDC on the main thread!
    }
}
