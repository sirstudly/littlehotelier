
package com.macbackpackers.jobs;

import java.net.URL;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.gargoylesoftware.htmlunit.AbstractPage;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.services.GmailService;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.VerifyAlexaLoggedInJob" )
public class VerifyAlexaLoggedInJob extends AbstractJob {

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
        String url = dao.getMandatoryOption( "hbo_alexa_verify_url" );

        WebRequest requestSettings = new WebRequest( new URL( url ), HttpMethod.POST );
        requestSettings.setAdditionalHeader( "Accept", "*/*" );
        requestSettings.setAdditionalHeader( "Content-Type", "application/json; charset=UTF-8" );
        requestSettings.setAdditionalHeader( "Cache-Control", "no-cache" );
        requestSettings.setAdditionalHeader( "Pragma", "no-cache" );
        requestSettings.setRequestBody( "{\"message\": \"volumelevel\"}" );
        AbstractPage statusPage = webClient.getPage( requestSettings );

        LOGGER.info( "do POST: " + url );
        LOGGER.info( "with body: " + requestSettings.getRequestBody() );

        String response = statusPage.getWebResponse().getContentAsString();
        LOGGER.info( "Response was: " + response );

        JsonObject rootElem = gson.fromJson( response, JsonObject.class );
        if ( null == rootElem || null == rootElem.get( "response" ) || StringUtils.isBlank( rootElem.get( "response" ).getAsString() )
                || false == rootElem.get( "response" ).getAsString().contains( "Kitchen Alexa Speaker" ) ) {
            LOGGER.info( "Sending email..." );
            gmail.sendEmail( dao.getMandatoryOption( "hbo_support_email" ), null, "Alexa Login Failed",
                    "Help! Alexa login failed. Has the cookie expired? -RONBOT" );
        }
    }
}
