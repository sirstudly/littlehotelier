
package com.macbackpackers.jobs;

import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Polymorphism;
import org.hibernate.annotations.PolymorphismType;
import org.springframework.beans.factory.annotation.Autowired;
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

}
