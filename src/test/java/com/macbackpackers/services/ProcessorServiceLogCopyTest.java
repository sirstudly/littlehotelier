package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.macbackpackers.dao.WordPressDAO;

/**
 * Verifies remote log copy is scheduled off the claim lock so workers are not blocked by scp.
 */
public class ProcessorServiceLogCopyTest {

    private ProcessorService processor;
    private WordPressDAO dao;

    @BeforeEach
    public void setUp() throws Exception {
        processor = new ProcessorService();
        dao = mock( WordPressDAO.class );
        setField( processor, "dao", dao );
        setField( processor, "localLogDirectory", "/tmp" );
    }

    @AfterEach
    public void tearDown() {
        processor.drainLogCopyExecutor();
    }

    @Test
    @Timeout( 5 )
    public void scheduleJobLogCopyDoesNotHoldClaimLock() throws Exception {
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

    private static void setField( Object target, String name, Object value ) throws Exception {
        Field field = target.getClass().getDeclaredField( name );
        field.setAccessible( true );
        field.set( target, value );
    }
}
