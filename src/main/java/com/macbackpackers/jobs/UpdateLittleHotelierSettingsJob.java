
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.services.AuthenticationService;

/**
 * Job that updates the settings for LittleHotelier.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.UpdateLittleHotelierSettingsJob" )
public class UpdateLittleHotelierSettingsJob extends AbstractJob {

    @Autowired
    @Transient
    private AuthenticationService authService;

    @Autowired
    @Transient
    private WordPressDAO wordpressDAO;

    @Override
    public void processJob() throws Exception {

        // this will throw an exception if unable to login 
        // using the parameters specified by the job
        authService.doLogin( getParameter( "username" ), getParameter( "password" ) );
        wordpressDAO.setOption( "hbo_lilho_username", getParameter( "username" ));
        wordpressDAO.setOption( "hbo_lilho_password", getParameter( "password" ));
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
