
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Job that invalidates the WordPress options cache so that the next access
 * will reload options from the database.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ReloadCacheJob" )
public class ReloadCacheJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        dao.invalidateOptionsCache();
    }

}
