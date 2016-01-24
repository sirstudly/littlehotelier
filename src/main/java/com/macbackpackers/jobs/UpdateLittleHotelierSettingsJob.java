
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

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

    @Override
    public void processJob() throws Exception {

        // this will throw an exception if unable to login 
        // using the parameters specified by the job
        authService.doLogin( getParameter( "username" ), getParameter( "password" ) );
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
