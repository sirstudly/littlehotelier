package com.macbackpackers.services;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.macbackpackers.jobs.AbstractJob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobProcessorThreadTest {

    @Test
    @Timeout( 5 )
    public void processesOneJobThenExitsInBatchModeWhenQueueIdle() {
        AbstractJob job = mock( AbstractJob.class );
        when( job.getId() ).thenReturn( 42 );

        ProcessorService processor = mock( ProcessorService.class );
        when( processor.isShutdownRequested() ).thenReturn( false );
        when( processor.getNextJobToProcess() )
                .thenReturn( job )
                .thenReturn( null )  // after processing
                .thenReturn( null ); // batch recheck
        when( processor.hasOutstandingJobs() ).thenReturn( false );

        new JobProcessorThread( processor, false, 50L, 10L, 10L ).run();

        verify( processor, times( 1 ) ).processJob( job );
        verify( processor, times( 3 ) ).getNextJobToProcess();
    }

    @Test
    @Timeout( 5 )
    public void batchModeRechecksAndProcessesSiblingCreatedJob() {
        AbstractJob first = mock( AbstractJob.class );
        AbstractJob second = mock( AbstractJob.class );
        when( first.getId() ).thenReturn( 1 );
        when( second.getId() ).thenReturn( 2 );

        ProcessorService processor = mock( ProcessorService.class );
        when( processor.isShutdownRequested() ).thenReturn( false );
        when( processor.getNextJobToProcess() )
                .thenReturn( first )
                .thenReturn( null )   // idle after first
                .thenReturn( second ) // found on batch recheck
                .thenReturn( null )
                .thenReturn( null );
        when( processor.hasOutstandingJobs() ).thenReturn( false );

        new JobProcessorThread( processor, false, 50L, 10L, 10L ).run();

        verify( processor ).processJob( first );
        verify( processor ).processJob( second );
    }

    @Test
    @Timeout( 5 )
    public void continuousModeIdlesUntilShutdown() throws Exception {
        AtomicBoolean shutdown = new AtomicBoolean( false );
        AtomicInteger claimCount = new AtomicInteger();

        ProcessorService processor = mock( ProcessorService.class );
        when( processor.isShutdownRequested() ).thenAnswer( inv -> shutdown.get() );
        when( processor.getNextJobToProcess() ).thenAnswer( inv -> {
            claimCount.incrementAndGet();
            return null;
        } );

        Thread worker = new Thread(
                new JobProcessorThread( processor, true, 200L, 20L, 10L ),
                "test-continuous-worker" );
        worker.start();

        // Wait until at least one idle claim cycle has happened
        long deadline = System.currentTimeMillis() + 2000L;
        while ( claimCount.get() < 1 && System.currentTimeMillis() < deadline ) {
            Thread.sleep( 20L );
        }
        assertTrue( claimCount.get() >= 1, "expected at least one claim attempt while idling" );

        shutdown.set( true );
        worker.join( 2000L );
        assertTrue( !worker.isAlive(), "worker should exit after shutdown" );
        verify( processor, never() ).processJob( org.mockito.ArgumentMatchers.any() );
    }

    @Test
    @Timeout( 5 )
    public void continuousModeProcessesJobsThenStopsOnShutdown() throws Exception {
        AbstractJob job = mock( AbstractJob.class );
        when( job.getId() ).thenReturn( 7 );

        AtomicBoolean shutdown = new AtomicBoolean( false );
        AtomicInteger processed = new AtomicInteger();

        ProcessorService processor = mock( ProcessorService.class );
        when( processor.isShutdownRequested() ).thenAnswer( inv -> shutdown.get() );
        when( processor.getNextJobToProcess() ).thenAnswer( inv -> {
            if ( processed.get() == 0 && !shutdown.get() ) {
                return job;
            }
            return null;
        } );
        org.mockito.Mockito.doAnswer( inv -> {
            processed.incrementAndGet();
            shutdown.set( true );
            return null;
        } ).when( processor ).processJob( job );

        Thread worker = new Thread(
                new JobProcessorThread( processor, true, 50L, 10L, 10L ),
                "test-process-then-shutdown" );
        worker.start();
        worker.join( 2000L );

        assertEquals( 1, processed.get() );
        assertTrue( !worker.isAlive() );
        verify( processor, times( 1 ) ).processJob( job );
    }
}
