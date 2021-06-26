
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.AbstractPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.services.GmailService;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.VerifyGoogleAssistantLoggedInJob" )
public class VerifyGoogleAssistantLoggedInJob extends AbstractJob {

    @Transient
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Transient
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    @Transient
    private GmailService gmail;

    @Autowired
    @Transient
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Override
    public void processJob() throws Exception {
        String url = dao.getMandatoryOption( "hbo_googleassist_verify_url" );
        AbstractPage statusPage = webClient.getPage( url );
        LOGGER.info( "Querying " + url );
        String response = statusPage.getWebResponse().getContentAsString();
        LOGGER.info( "Response was: " + response );
        JsonObject rootElem = gson.fromJson( response, JsonObject.class );
        if ( null == rootElem || null == rootElem.get( "message" ) || StringUtils.isBlank( rootElem.get( "message" ).getAsString() ) ) {
            LOGGER.info( "Sending email..." );
            gmail.sendEmail( dao.getMandatoryOption( "hbo_support_email" ), null, "Google Assistant Failed",
                    "Help! Google Assistant credentials expired?! -RONBOT" );
        }
    }
}
