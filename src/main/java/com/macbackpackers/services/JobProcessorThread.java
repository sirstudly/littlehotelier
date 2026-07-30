package com.macbackpackers.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macbackpackers.jobs.AbstractJob;

/**
 * Claims jobs from the queue and processes them until idle (batch) or shutdown (continuous).
 */
public class JobProcessorThread implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger( JobProcessorThread.class );

    /** Default idle backoff when the queue is empty in continuous (server) mode. */
    public static final long DEFAULT_IDLE_SLEEP_MILLIS = 3_000L;

    /** Slice size so shutdown is observed quickly while idling. */
    public static final long DEFAULT_IDLE_SLICE_MILLIS = 500L;

    /** Brief pause before re-claiming in batch mode so sibling-created jobs are picked up. */
    public static final long DEFAULT_BATCH_RECHECK_MILLIS = 500L;

    private final ProcessorService processorService;
    private final boolean continuous;
    private final long idleSleepMillis;
    private final long idleSliceMillis;
    private final long batchRecheckMillis;

    /**
     * @param processorService job claim/process collaborator
     * @param continuous true for server mode (idle-sleep forever until shutdown); false for batch
     */
    public JobProcessorThread( ProcessorService processorService, boolean continuous ) {
        this( processorService, continuous,
                DEFAULT_IDLE_SLEEP_MILLIS, DEFAULT_IDLE_SLICE_MILLIS, DEFAULT_BATCH_RECHECK_MILLIS );
    }

    /**
     * @param processorService job claim/process collaborator
     * @param continuous true for server mode
     * @param idleSleepMillis total idle sleep when continuous and queue empty
     * @param idleSliceMillis max sleep slice while checking shutdown
     * @param batchRecheckMillis pause before batch idle recheck
     */
    public JobProcessorThread( ProcessorService processorService, boolean continuous,
            long idleSleepMillis, long idleSliceMillis, long batchRecheckMillis ) {
        this.processorService = processorService;
        this.continuous = continuous;
        this.idleSleepMillis = idleSleepMillis;
        this.idleSliceMillis = idleSliceMillis;
        this.batchRecheckMillis = batchRecheckMillis;
    }

    @Override
    public void run() {
        String workerName = Thread.currentThread().getName();
        LOGGER.info( "Worker {} started (continuous={})", workerName, continuous );
        try {
            while ( !processorService.isShutdownRequested() ) {
                try {
                    AbstractJob job = processorService.getNextJobToProcess();
                    if ( job != null ) {
                        LOGGER.info( "LOCKED job {} by {}", job.getId(), workerName );
                        processorService.processJob( job );
                        continue;
                    }

                    if ( continuous ) {
                        idleSleep( idleSleepMillis );
                        continue;
                    }

                    // Batch mode: briefly recheck so child jobs created by siblings are claimed
                    idleSleep( batchRecheckMillis );
                    if ( processorService.isShutdownRequested() ) {
                        break;
                    }
                    job = processorService.getNextJobToProcess();
                    if ( job != null ) {
                        LOGGER.info( "LOCKED job {} by {}", job.getId(), workerName );
                        processorService.processJob( job );
                        continue;
                    }
                    if ( processorService.hasOutstandingJobs() ) {
                        // Dependents or sibling in-flight work may still produce claimable jobs
                        continue;
                    }
                    LOGGER.info( "Worker {} exiting; queue idle", workerName );
                    break;
                }
                catch ( InterruptedException e ) {
                    Thread.currentThread().interrupt();
                    LOGGER.info( "Worker {} interrupted; exiting", workerName );
                    break;
                }
                catch ( Exception ex ) {
                    LOGGER.error( "Unexpected error in worker {}; continuing", workerName, ex );
                }
            }
        }
        finally {
            LOGGER.info( "Worker {} stopped", workerName );
        }
    }

    /**
     * Sleeps up to {@code totalMillis} in slices so {@link ProcessorService#isShutdownRequested()}
     * is observed promptly.
     */
    private void idleSleep( long totalMillis ) throws InterruptedException {
        long remaining = totalMillis;
        while ( remaining > 0 && !processorService.isShutdownRequested() ) {
            long slice = Math.min( idleSliceMillis, remaining );
            Thread.sleep( slice );
            remaining -= slice;
        }
    }
}
