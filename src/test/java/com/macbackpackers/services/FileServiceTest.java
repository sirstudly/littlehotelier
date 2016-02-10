
package com.macbackpackers.services;

import java.io.File;
import java.nio.channels.FileLock;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class FileServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( FileServiceTest.class );

    @Autowired
    FileService fs;

    @Test
    public void testLockFileSingleProcess() throws Exception {
        FileLock lock = fs.lockFile( new File( "test.lock" ) );
        Assert.assertEquals( "Expecting lock to be valid", true, lock.isValid() );
        Assert.assertEquals( "Expecting exclusive lock", false, lock.isShared() );
    }

    @Test
    public void testLockFile() throws Exception {
        Process p1 = Runtime.getRuntime().exec( "runFileServiceTest.cmd" );
        sleep( 2000 );

        Process p2 = Runtime.getRuntime().exec( "runFileServiceTest.cmd" );
        Assert.assertEquals( "Expected second process to exit immediately", 1, p2.waitFor() );

        int exitCode = p1.waitFor();
        Assert.assertEquals( "Expected first process to exit normally", 0, exitCode );
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
