
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.HostelworldScraper;

/**
 * Job that updates the settings for Hostelworld.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.UpdateHostelworldSettingsJob" )
public class UpdateHostelworldSettingsJob extends AbstractJob {

    @Autowired
    @Transient
    private HostelworldScraper hwScraper;

    @Autowired
    @Transient
    private WordPressDAO wordpressDAO;

    @Override
    public void processJob() throws Exception {

        // this will throw an exception if unable to login 
        // using the parameters specified by the job
        hwScraper.doLogin( getParameter( "username" ), getParameter( "password" ) );
        wordpressDAO.setOption( "hbo_hw_username", getParameter( "username" ));
        wordpressDAO.setOption( "hbo_hw_password", getParameter( "password" ));
    }

    @Override
    public void finalizeJob() {
        hwScraper.closeAllWindows(); // cleans up JS threads
    }

    /**
     * To avoid locking out the account, overrides method to retry only once.
     * 
     * @return 1
     */
    @Override
    public int getRetryCount() {
        return 1;
    }

}
