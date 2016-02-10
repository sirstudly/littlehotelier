
package com.macbackpackers.services;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** the title on the login page */
    public static final String LOGIN_PAGE_TITLE = "Welcome to Little Hotelier";

    @Autowired
    @Qualifier( "webClientScriptingDisabled" )
    private WebClient localWebClient;

    @Autowired
    private FileService fileService;

    @Autowired
    private Environment env;

    /**
     * Loads the previous credentials and attempts to go to a specific page. If we get redirected
     * back to the login page, then this method will fail with an unrecoverable fault.
     * 
     * @param pageURL the page to go to
     * @return the loaded page
     * @throws IOException if unable to read previously saved credentials
     * @throws UnrecoverableFault if credentials were not successful
     */
    public HtmlPage goToPage( String pageURL, WebClient webClient ) throws IOException, UnrecoverableFault {
        fileService.loadCookiesFromFile( webClient );

        // attempt to go the page directly using the current credentials
        HtmlPage nextPage = webClient.getPage( pageURL );

        // if we get redirected back to login, then login and try again
        if ( LOGIN_PAGE_TITLE.equals( nextPage.getTitleText() ) ) {
            LOGGER.warn( "Current credentials not valid?" );
            throw new UnrecoverableFault( "Unable to login using existing credentials. Has the password changed?" );
        }
        return nextPage;
    }

    /**
     * Logs into the application and writes the credentials to file so we don't have to do it again.
     * 
     * @param username the username to use
     * @param password the password to use
     * @throws IOException on write error
     * @throws UnrecoverableFault if unable to login; cookie file not updated in this case
     */
    public void doLogin( String username, String password ) throws IOException {

        HtmlPage loginPage = localWebClient.getPage( env.getProperty( "lilhotelier.url.login" ) );

        // The form doesn't have a name so just take the only one on the page
        List<HtmlForm> forms = loginPage.getForms();
        HtmlForm form = forms.iterator().next();

        HtmlSubmitInput button = form.getInputByName( "commit" );
        HtmlTextInput usernameField = form.getInputByName( "user_session[username]" );
        HtmlPasswordInput passwordField = form.getInputByName( "user_session[password]" );

        // Change the value of the text field
        usernameField.setValueAttribute( username );
        passwordField.setValueAttribute( password );

        HtmlPage nextPage = button.click();
        LOGGER.info( "Finished logging in" );
        LOGGER.info( nextPage.asXml() );

        if ( LOGIN_PAGE_TITLE.equals( nextPage.getTitleText() ) ) {
            throw new UnrecoverableFault( "Unable to login. Incorrect password?" );
        }
        fileService.writeCookiesToFile( localWebClient );
    }

    /**
     * Logs into the application using the environment properties and writes the credentials to file
     * so we don't have to do it again.
     * 
     * @throws IOException on write error
     * @throws UnrecoverableFault if unable to login; cookie file not updated in this case
     */
    public void doLogin() throws IOException {
        doLogin( env.getProperty( "lilhotelier.username" ), env.getProperty( "lilhotelier.password" ) );
    }

}
