package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.macbackpackers.dao.WordPressDAO;

/**
 * Verifies remote log copy is scheduled off the claim lock so workers are not blocked by scp.
 */
public class ProcessorServiceLogCopyTest {

    private ProcessorService processor;

    @AfterEach
    public void tearDown() {
        if ( processor != null ) {
            processor.drainLogCopyExecutor();
        }
    }

    @Test
    @Timeout( 5 )
    public void scheduleJobLogCopyDoesNotHoldClaimLock() throws Exception {
        processor = new ProcessorService();
        WordPressDAO dao = mock( WordPressDAO.class );
        setField( processor, "dao", dao );
        setField( processor, "localLogDirectory", "/tmp" );

        CountDownLatch copyStarted = new CountDownLatch( 1 );
        CountDownLatch releaseCopy = new CountDownLatch( 1 );
        AtomicBoolean claimAcquiredWhileCopyRunning = new AtomicBoolean( false );

        when( dao.getOption( anyString() ) ).thenAnswer( inv -> {
            copyStarted.countDown();
            assertTrue( releaseCopy.await( 3, TimeUnit.SECONDS ) );
            return null; // skip gzip/scp body
        } );

        processor.scheduleJobLogCopy( 99 );
        assertTrue( copyStarted.await( 2, TimeUnit.SECONDS ), "copy should start on log-copy executor" );

        Thread claimThread = new Thread( () -> {
            synchronized ( processor ) {
                claimAcquiredWhileCopyRunning.set( true );
            }
        }, "test-claim" );
        claimThread.start();
        claimThread.join( 1000L );

        assertTrue( claimAcquiredWhileCopyRunning.get(),
                "synchronized(processor) used by getNextJobToProcess must not be blocked by log copy" );

        releaseCopy.countDown();
        claimThread.join( 1000L );
    }

    @Test
    @Timeout( 5 )
    public void waitForProcessOrDestroyReturnsExitCodeWhenProcessFinishes() throws Exception {
        processor = new ProcessorService();
        Process process = new ProcessBuilder( "true" ).start();
        assertEquals( 0, processor.waitForProcessOrDestroy( process, "true", 2 ) );
    }

    @Test
    @Timeout( 10 )
    public void waitForProcessOrDestroyDestroysHungProcessAndReturnsNonZero() throws Exception {
        processor = new ProcessorService();
        Process process = new ProcessBuilder( "sleep", "60" ).start();
        int exitVal = processor.waitForProcessOrDestroy( process, "sleep", 1 );
        assertNotEquals( 0, exitVal, "timed-out process should not look like success" );
        assertFalse( process.isAlive(), "child should be destroyed after timeout" );
    }

    private static void setField( Object target, String name, Object value ) throws Exception {
        Field field = target.getClass().getDeclaredField( name );
        field.setAccessible( true );
        field.set( target, value );
    }
}
