package com.macbackpackers.services;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.jobs.AbstractJob;

/**
 * Takes a job from the job queue and processes it.
 */
public class JobProcessorThread implements Runnable {

    private static final Logger LOGGER =  LoggerFactory.getLogger( JobProcessorThread.class );

    @Autowired
    private ProcessorService processorService;
    
    // assuming we use a threadpool to run all the outstanding jobs,
    // we can terminate the thread pool once all threads try to retrieve a job
    // to run but can't find one
    // this barrier will synchronize at the point where all threads terminate
    // and check that we have nothing left to do
    private CyclicBarrier barrier;

    /**
     * Default constructor.
     * 
     * @param barrier the common barrier used for all threads within this pool
     */
    public JobProcessorThread( CyclicBarrier barrier ) {
        this.barrier = barrier;
    }

    /**
     * Process all jobs. If no jobs are available to be run, then pause for a configured period
     * before checking again.
     */
    @Override
    public void run() {
        while ( true ) {
            try {
                if ( processorService.isShutdownRequested() ) {
                    LOGGER.info( "Shutdown requested; worker {} exiting", Thread.currentThread().getName() );
                    break;
                }

                // find and run all submitted jobs
                for ( AbstractJob job = processorService.getNextJobToProcess() ;
                        job != null ;
                        job = processorService.isShutdownRequested() ? null : processorService.getNextJobToProcess() ) {
                    LOGGER.info( "LOCKED job " + job.getId() + " by " + Thread.currentThread().getName() );
                    processorService.processJob( job );

                    // before we continue with any other jobs (possibly ones we just created),
                    // reset the barrier (so we release any suspended threads so they can help us out)
                    barrier.reset();
                }

                if ( processorService.isShutdownRequested() ) {
                    LOGGER.info( "Shutdown requested after draining claimed jobs; worker {} exiting",
                            Thread.currentThread().getName() );
                    break;
                }
                
                // no more jobs to run at the moment; wait until all jobs are also at this point
                barrier.await();
                
                // if we manage to get here, then all other threads have also called await()
                // ie. we have no jobs remaining; so our job is done
                break;
            }
            catch ( InterruptedException e ) {
                if ( processorService.isShutdownRequested() ) {
                    LOGGER.info( "Worker {} interrupted during shutdown; exiting", Thread.currentThread().getName() );
                    break;
                }
                LOGGER.debug( "Thread interrupted, ignoring..." );
            }
            catch ( BrokenBarrierException ex ) {
                if ( processorService.isShutdownRequested() ) {
                    LOGGER.info( "Barrier broken during shutdown; worker {} exiting", Thread.currentThread().getName() );
                    break;
                }
                // ordinarily if any other threads terminate abruptly, this will be thrown
                // but since we catch all other exceptions, the only way this is thrown
                // is if we call reset() on the barrier (which we do once we finish a task)
                // and so allow other threads to handle any added tasks
                LOGGER.debug( "Cyclic barrier opened; checking for any outstanding tasks" );
            }
            catch ( Exception ex ) {
                LOGGER.error( "Received error but continuing...", ex );
            }
        }
    }

}
