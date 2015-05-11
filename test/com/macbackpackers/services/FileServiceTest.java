package com.macbackpackers.services;

import java.io.File;
import java.nio.channels.FileLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

public class FileServiceTest {

    private static Logger logger = LogManager.getLogger( FileServiceTest.class );

    FileService fs = new FileService();

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
        synchronized (lock) {
            try {
                lock.wait( millis );
            } catch ( InterruptedException e ) {
                // woke up
            }
        }
    }

    /**
     * Needed so we can create multiple processes for this test.
     * 
     * @param argv
     *            no args req'd
     */
    public static void main( String argv[] ) throws Exception {
        FileService fs = new FileService();
        FileLock lock = fs.lockFile( new File( "test.lock" ) );

        // if we didn't get the lock, a process is already running
        if ( lock == null ) {
            logger.error( "Process is already running" );
            throw new Exception( "Process ia already running" );
        }

        logger.info( "lock is valid: " + lock.isValid() );
        logger.info( "lock is shared: " + lock.isShared() );

        // if lock is acquired, hold for 10 seconds
        sleep( 6000 );

        // release file lock
        lock.release();
    }

}
