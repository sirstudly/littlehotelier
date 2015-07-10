
package com.macbackpackers.jobs;

import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;
import org.hibernate.annotations.Polymorphism;
import org.hibernate.annotations.PolymorphismType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Some framework code associated with executing jobs.
 *
 */
@Entity
@Component
@Scope( "prototype" )
@Polymorphism( type = PolymorphismType.EXPLICIT )
public abstract class AbstractJob extends Job {

    @Transient
    protected final Logger LOGGER = LogManager.getLogger( getClass() );

    @Autowired
    @Transient
    protected WordPressDAO dao;

    /**
     * Do whatever it is we need to do.
     * 
     * @throws Exception
     */
    public abstract void processJob() throws Exception;

    /**
     * Runs the job and updates the status when complete.
     */
    public void doProcessJob() {
        dao.updateJobStatus( getId(), JobStatus.processing, JobStatus.submitted );
        NDC.push( String.valueOf( getId() ) ); // record the ID of this job for logging

        try {
            LOGGER.info( "Processing job " + getId() );
            processJob();
            LOGGER.info( "Finished job " + getId() );
            dao.updateJobStatus( getId(), JobStatus.completed, JobStatus.processing );
        }
        catch ( Throwable ex ) {
            LOGGER.error( "Error occurred when running " + getClass().getSimpleName() + " id: " + getId(), ex );
            dao.updateJobStatus( getId(), JobStatus.failed, JobStatus.processing );
        }
        finally {
            NDC.pop();
        }
    }

}
