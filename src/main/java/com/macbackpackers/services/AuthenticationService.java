package com.macbackpackers.services;

import java.io.IOException;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.macbackpackers.exceptions.UnrecoverableFault;

@Service
public class AuthenticationService {
    
    private final Logger LOGGER = LogManager.getLogger(getClass());

    /** the title on the login page */
    public static final String LOGIN_PAGE_TITLE = "Welcome to Little Hotelier";

    @Autowired
    private ObjectFactory<WebClient> webClientFactory;
    
    @Autowired
    private FileService fileService;

    @Autowired
    private Environment env;
    
    /**
     * Loads the previous credentials and attempts to go to a specific page. If we 
     * get redirected back to the login page, then this method will fail with
     * an unrecoverable fault.
     * 
     * @param pageURL the page to go to
     * @return the loaded page
     * @throws IOException if unable to read previously saved credentials
     * @throws UnrecoverableFault if credentials were not successful
     */
    public HtmlPage loginAndGoToPage( String pageURL, WebClient webClient ) throws IOException, UnrecoverableFault {
        fileService.loadCookiesFromFile( webClient );
        
        // attempt to go the page directly using the current credentials
        HtmlPage nextPage = webClient.getPage( pageURL );
        
        // if we get redirected back to login, then login and try again
        if( LOGIN_PAGE_TITLE.equals( nextPage.getTitleText() ) ) {
            LOGGER.warn( "Current credentials not valid?" );
            throw new UnrecoverableFault( "Unable to login using existing credentials. Has the password changed?" );
        }
        return nextPage;
    }

    /**
     * Logs into the application and writes the credentials to file so we
     * don't have to do it again.
     * 
     * @throws IOException on write error
     */
    public void doLogin() throws IOException {

        WebClient webClient = webClientFactory.getObject();
        HtmlPage loginPage = webClient.getPage( env.getProperty( "lilhotelier.url.login" ) );

        // The form doesn't have a name so just take the only one on the page
        List<HtmlForm> forms = loginPage.getForms();
        HtmlForm form = forms.iterator().next();

        HtmlSubmitInput button = form.getInputByName( "commit" );
        HtmlTextInput usernameField = form.getInputByName( "user_session[username]" );
        HtmlPasswordInput passwordField = form.getInputByName( "user_session[password]" );

        // Change the value of the text field
        usernameField.setValueAttribute( env.getProperty( "lilhotelier.username" ) );
        passwordField.setValueAttribute( env.getProperty( "lilhotelier.password" ) );

        HtmlPage nextPage = button.click();
        LOGGER.info( "Finished logging in" );
        LOGGER.info( nextPage.asXml() );
        
        if( LOGIN_PAGE_TITLE.equals( nextPage.getTitleText() ) ) {
            throw new UnrecoverableFault( "Unable to login. Incorrect password?" );
        }
        fileService.writeCookiesToFile( webClient );
    }
    
}