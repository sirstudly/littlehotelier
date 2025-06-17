
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.FileService;

/**
 * Job that updates the settings for LittleHotelier.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.UpdateLittleHotelierSettingsJob" )
public class UpdateLittleHotelierSettingsJob extends AbstractJob {

    @Autowired
    @Transient
    private FileService fileService;

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {

        // this will throw an exception if unable to login 
        // using the parameters specified by the job
        // *disabled for now - not working*
//        authService.doLogin( webClient, getParameter( "username" ), getParameter( "password" ) );
//        dao.setOption( "hbo_lilho_username", getParameter( "username" ));
//        dao.setOption( "hbo_lilho_password", getParameter( "password" ));

        // update the session cookie and write it to disk
        fileService.loadCookiesFromFile( webClient );
        fileService.addLittleHotelierSessionCookie( webClient, dao.getOption( "hbo_lilho_session" ) );
        fileService.writeCookiesToFile( webClient );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
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
