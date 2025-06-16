package com.macbackpackers.services;

import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.Cookie;
import org.htmlunit.util.NameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class AuthenticationServiceTest {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    AuthenticationService authService;
    
    @Autowired
    @Qualifier( "webClient" )
    private WebClient webClient;
    
    @Test
    public void testLogin() throws Exception {
        HtmlPage nextPage = webClient.getPage( "https://login.littlehotelier.com/login/auth" );
        String domain = getLoginScriptVars( nextPage.asXml(), "domain: '(.*)'" );
        String clientID = getLoginScriptVars( nextPage.asXml(), "clientID: '(.*)'" );
        String callbackURL = getLoginScriptVars( nextPage.asXml(), "callbackURL: '(.*)'" );
        String connection = getLoginScriptVars( nextPage.asXml(), "connection: '(.*)'" );
        LOGGER.info( "domain: " + domain + " clientID: " + clientID + " callbackURL: " + callbackURL );
        
        WebRequest loginRequest = new WebRequest( 
                new URL("https://siteminder-prod-nexus.auth0.com/usernamepassword/login"), 
                HttpMethod.POST);
        loginRequest.setAdditionalHeader( "Auth0-Client", "eyJuYW1lIjoiYXV0aDAuanMiLCJ2ZXJzaW9uIjoiNy42LjEifQ" );
        loginRequest.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded" );
        loginRequest.setAdditionalHeader( "Host", domain );
        loginRequest.setAdditionalHeader( "Origin", "https://login.littlehotelier.com" );
        loginRequest.setAdditionalHeader( "Referer", "https://login.littlehotelier.com/login/auth" );
        
        ArrayList<NameValuePair> params = new ArrayList<>();
        params.add( new NameValuePair( "scope" , "openid" ) );
        params.add( new NameValuePair( "response_type" , "code" ) );
        params.add( new NameValuePair( "connection" , connection ) );
        params.add( new NameValuePair( "username" , "castlerock@macbackpackers.com" ) );
        params.add( new NameValuePair( "password" , "Whisky20" ) );
        params.add( new NameValuePair( "sso" , "true" ) );
        params.add( new NameValuePair( "client_id" , clientID ) );
        params.add( new NameValuePair( "redirect_uri" , callbackURL ) );
        params.add( new NameValuePair( "tenant" , "siteminder-prod-nexus" ) );
        loginRequest.setRequestParameters( params );
        
        //"scope=openid&response_type=code&connection=NexusProd&username=castlerock%40macbackpackers.com&password=Whisky20&sso=true&client_id=rDHkmoJDetyELo6zOI2Dv5RzEaF2YlAT&redirect_uri=https%3A%2F%2Flogin.littlehotelier.com%2Fauth0%2Fcallback&tenant=siteminder-prod-nexus"
        HtmlPage redirectPage = webClient.getPage(loginRequest);
        LOGGER.info( redirectPage.asXml() );
        String wctx = getFormInput( redirectPage, "wctx" );
        String wa = getFormInput( redirectPage, "wa" );
        String wresult = getFormInput( redirectPage, "wresult" );
        LOGGER.info( "wctx: " + wctx );
        LOGGER.info( "wa: " + wa );
        LOGGER.info( "wresult: " + wresult );
        
        Cookie c = redirectPage.getWebClient().getCookieManager().getCookie( "auth0" );
        LOGGER.info( "auth0 Cookie: " + (c == null ? "null" : c.getValue() ));

        String actionURL = getFormAction( redirectPage, "hiddenform" );
        LOGGER.info( "POST to: " + actionURL );
        loginRequest = new WebRequest( new URL( actionURL ), HttpMethod.POST );
        loginRequest.setAdditionalHeader( "Auth0-Client", "eyJuYW1lIjoiYXV0aDAuanMiLCJ2ZXJzaW9uIjoiNy42LjEifQ" );
        loginRequest.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded" );
        loginRequest.setAdditionalHeader( "Host", domain );
        loginRequest.setAdditionalHeader( "Origin", "https://login.littlehotelier.com" );
        loginRequest.setAdditionalHeader( "Referer", "https://login.littlehotelier.com/login/auth" );
        loginRequest.setAdditionalHeader( "Cookie", "auth0=" + c.getValue() );
        
        params = new ArrayList<>();
        params.add( new NameValuePair( "wa" , wa) );
        params.add( new NameValuePair( "wresult" , wresult ) );
        params.add( new NameValuePair( "wctx" , wctx ) );
        loginRequest.setRequestParameters( params );

        Page postResponse = webClient.getPage(loginRequest);
        LOGGER.info( postResponse.getWebResponse().getContentAsString() );

        c = redirectPage.getWebClient().getCookieManager().getCookie( "auth0" );
        LOGGER.info( "auth0 Cookie: " + (c == null ? "null" : c.getValue() ));

    }

    private String getFormInput( HtmlPage redirectPage, String elementName ) {
        HtmlInput inputElem = redirectPage.getFirstByXPath(
                String.format( "//input[@name='%s']", elementName ) );
        String returnVal = inputElem.getAttribute( "value" );
        if ( returnVal == null ) {
            throw new IllegalStateException( "Missing element " + elementName );
        }
        return returnVal;
    }

    private String getFormAction( HtmlPage redirectPage, String elementName ) {
        HtmlForm formElem = redirectPage.getFirstByXPath( 
                String.format( "//form[@name='%s']", elementName ) );
        String returnVal = formElem.getAttribute( "action" );
        if ( returnVal == null ) {
            throw new IllegalStateException( "Missing element " + elementName );
        }
        return returnVal;
    }

    private String getLoginScriptVars( String loginPage, String regex ) {
        Pattern p = Pattern.compile( regex );
        Matcher m = p.matcher( loginPage );
        if ( m.find() ) {
            return m.group( 1 );
        }
        throw new IllegalStateException( "Unable to find content " + regex + " on login page" );
    }
    
    @Test
    public void testIsRoyalMileBackpackers() throws Exception {
        LOGGER.info( "Is RMB? " + authService.isRoyalMileBackpackers() );
    }
    
    @Test
    public void testGoToPageSkippingLogin() throws Exception {
        HtmlPage page = authService.goToPage( "https://app.littlehotelier.com/extranet/reports/summary?property_id=526", webClient );
        LOGGER.debug( page.asXml() );
    }

    @Test
    public void testUpdateLittleHotelierSessionKey() throws Exception {
        authService.updateLittleHotelierSessionKey( webClient );
    }

    @Test
    public void testGetTotpPassword() {
        LOGGER.info( "" + authService.getTotpPassword( "XXXXXXXXXXXXXXXXXXXXXXXX" ) );
    }

    @Test
    public void testFetchCloudbedsGoogleAuth2faCode() {
        LOGGER.info( "2fa code: " + authService.fetchCloudbedsGoogleAuth2faCode() );
    }
}