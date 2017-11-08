
package com.macbackpackers.jobs;

import javax.persistence.Entity;
import javax.persistence.Transient;

import org.hibernate.annotations.Polymorphism;
import org.hibernate.annotations.PolymorphismType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.Job;
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
    protected final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Transient
    protected WordPressDAO dao;

    @Value( "${process.jobs.retries:3}" )
    @Transient
    private int numberRetries;
    
    /**
     * Resets the job back to the 'unrun' state. We don't need this
     * if {@link #processJob()} runs as a single transaction but
     * due to server timeouts; this isn't usually so.
     */
    public void resetJob() throws Exception {
        // override to implement
    }

    /**
     * Cleans up any resources after <em>all attempts</em> to run the job have finished.
     */
    public void finalizeJob() {
        // override to implement
    }

    /**
     * Do whatever it is we need to do.
     * 
     * @throws Exception
     */
    public abstract void processJob() throws Exception;

    /**
     * Returns the number of attempts to run this job before aborting with a failure.
     * 
     * @return retry count
     */
    public int getRetryCount() {
        return numberRetries;
    }

}
