package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;

import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.config.LittleHotelierConfig;

@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class FileServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( FileServiceTest.class );

    @Autowired
    @Qualifier( "webClient" )
    WebClient webClient;

    @Autowired
    FileService fs;

    @Test
    public void testLockFileSingleProcess() throws Exception {
        FileLock lock = fs.lockFile( new File( "test.lock" ) );
        assertTrue( lock.isValid(), "Expecting lock to be valid" );
        assertTrue( !lock.isShared(), "Expecting exclusive lock" );
    }

    @Test
    public void testLockFile() throws Exception {
        Process p1 = Runtime.getRuntime().exec( "runFileServiceTest.cmd" );
        sleep( 2000 );

        Process p2 = Runtime.getRuntime().exec( "runFileServiceTest.cmd" );
        assertEquals( 1, p2.waitFor(), "Expected second process to exit immediately" );

        int exitCode = p1.waitFor();
        assertEquals( 0, exitCode, "Expected first process to exit normally" );
    }

    @Test
    public void testSerializeDeserializeToFromFile() throws Exception {
        Integer i = 2015;
        fs.serializeObjectToFile( i, "test.object" );
        assertEquals( Integer.valueOf( 2015 ), fs.deserializeObjectFromFile( "test.object", Integer.class ) );
    }

    @Test
    public void dumpLHSessionId() throws IOException {
        fs.loadCookiesFromFile( webClient );
        LOGGER.info( webClient.getCookieManager().getCookie( "_littlehotelier_session" ).getValue() );
    }

    private static void sleep( int millis ) {
        Object lock = new Object();
        synchronized ( lock ) {
            try {
                lock.wait( millis );
            }
            catch ( InterruptedException e ) {
                // woke up
            }
        }
    }

    /**
     * Needed so we can create multiple processes for this test.
     *
     * @param argv no args req'd
     */
    public static void main( String argv[] ) throws Exception {
        FileService fs = new FileService();
        FileLock lock = fs.lockFile( new File( "test.lock" ) );

        // if we didn't get the lock, a process is already running
        if ( lock == null ) {
            LOGGER.error( "Process is already running" );
            throw new Exception( "Process ia already running" );
        }

        LOGGER.info( "lock is valid: " + lock.isValid() );
        LOGGER.info( "lock is shared: " + lock.isShared() );

        // if lock is acquired, hold for 10 seconds
        sleep( 10000 );

        // release file lock
        lock.release();
    }
}
