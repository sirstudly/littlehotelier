package com.macbackpackers.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.exceptions.UnrecoverableFault;

/**
 * Uses the Gmail API to query a Gmail inbox. 
 * @see https://developers.google.com/gmail/api/quickstart/java
 */
@Service
public class GmailService {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** Application name. */
    private static final String APPLICATION_NAME =
        "Query Email using Gmail API";

    /** Directory to store user credentials for this application. */
    @Value( "${user.credentials.directory}" )
    private String userCredentialsDirectory;

    /** This is the OAuth 2.0 Client ID used to login to Gmail */
    @Value( "${gmail.oauth.client.id.file}" )
    private String oauthClientIdFile;

    /** The default gmail user to query */
    private static final String GMAIL_USER = "me";

    /** String for matching emails against Booking.com */
    private final String BDC_MATCH_TEMPLATE = "(subject:\"Booking.com Booking # %s\" OR subject:\"Booking.com Modification for Booking # %s\") from:noreply@littlehotelier.com";
    
    /** String for matching emails for LH security access */
    private final String LH_SECURITY_ACCESS = "subject:\"LittleHotelier - Security access required\" from:noreply@littlehotelier.com in:inbox"; 

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY =
        JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** 
     * Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at DATA_STORE_DIR
     */
    private static final List<String> SCOPES =
        Arrays.asList(GmailScopes.GMAIL_READONLY);

    public GmailService() {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        }
        catch ( GeneralSecurityException | IOException e ) {
            throw new UnrecoverableFault( e.getMessage(), e );
        }
    }

    /**
     * Creates an authorized Credential object. First time this runs will open
     * a web window to confirm authorisation. This only needs to be done once
     * and the credentials can be copied elsewhere.
     * 
     * @return an authorized Credential object.
     * @throws IOException
     */
    public Credential authorize() throws IOException {
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, getGmailClientSecret(), SCOPES)
                .setDataStoreFactory(getCredentialsDataStoreFactory())
                .setAccessType("offline")
                .build();
        Credential credential = new AuthorizationCodeInstalledApp(
            flow, new LocalServerReceiver()).authorize("user");
        LOGGER.info( "Credentials saved to " + userCredentialsDirectory );
        return credential;
    }

    /**
     * Build and return an authorized Gmail client service.
     * @return an authorized Gmail client service
     * @throws IOException
     */
    public Gmail connectAsClient() throws IOException {
        Credential credential = authorize();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Fetch the card details from the BDC email with the given booking reference.
     * 
     * @param bookingRef BDC booking reference
     * @return non-null card details (card details will be masked; last 4 digits visible)
     * @throws IOException on I/O error
     */
    public CardDetails fetchBdcCardDetailsFromBookingRef( String bookingRef ) throws IOException {
        
        if ( false == bookingRef.startsWith( "BDC-" ) ) {
            throw new IllegalArgumentException( "Unsupporting booking " + bookingRef );
        }

        LOGGER.info( "Looking up BDC card details for booking " + bookingRef );
        String bookingId = bookingRef.substring( 4 ); // BDC-(bookingId)
        Gmail service = connectAsClient();
        ListMessagesResponse listResponse = 
                service.users().messages()
                .list( GMAIL_USER )
                .setQ( String.format( BDC_MATCH_TEMPLATE, bookingId, bookingId ) )
                .execute();
        List<Message> messages = listResponse.getMessages();
        if ( messages.size() == 0 ) {
            throw new EmptyResultDataAccessException( "No messages found for BDC " + bookingId, 1 );
        }
        else {
            LOGGER.info( messages.size() + " messages:" );
            for ( Message message : messages ) {
                LOGGER.info( message.getId() + " - " + message.getThreadId() );
                String body = fetchMessageBody( service, message.getId(), bookingId );
                
                try {
                    return new CardDetails(
                            findAndReturn( "^Name \\.+: (.*)$", body ),
                            findAndReturn( "^Number \\.+: (.*)$", body ),
                            findAndReturn( "^Expiry \\.+: (.*)$", body ),
                            findAndReturn( "^CVC \\.+: (.*)$", body ) );
                }
                catch ( EmptyResultDataAccessException e ) {
                    LOGGER.info( "Unable to find card details for messsageId " + message.getId() + "; continuing", e );
                }
            }
        }
        throw new EmptyResultDataAccessException( "No valid messages found with card details for BDC " + bookingId, 1 );
    }

    /**
     * Returns the LH security token from the most recent email.
     * 
     * @return non-null security token
     * @throws IOException on I/O error
     * @throws EmptyResultDataAccessException if no message found
     */
    public String fetchLHSecurityToken() throws IOException, EmptyResultDataAccessException {
        for(int i = 0; i < 3; i++) {
            Gmail service = connectAsClient();
            ListMessagesResponse listResponse = 
                    service.users().messages()
                    .list( GMAIL_USER )
                    .setQ( LH_SECURITY_ACCESS )
                    .execute();
            List<Message> messages = listResponse.getMessages();
            if ( messages != null ) {
                LOGGER.info( messages.size() + " messages:" );
                for ( Message message : messages ) {
                    LOGGER.info( message.getId() + " - " + message.getThreadId() );
                    String body = fetchMessageBody( service, message.getId() );
                    if ( body == null ) {
                        throw new EmptyResultDataAccessException( "No body found in LH security token message?", 1 );
                    }
                    return findAndReturn( "^<strong>(\\d{6})</strong>$", body );
                }
            }
            
            // email hasn't arrived yet? backoff.. 30 sec
            try {
                Thread.sleep( 30000 );
            }
            catch ( InterruptedException e ) {
                // ignore
            }
        }
        throw new EmptyResultDataAccessException( "No LH security token messages found", 1 );
    }

    /**
     * Returns the message body for the given message ID.
     *  
     * @param gmail the connected Gmail client
     * @param messageId the unique message ID to retrieve
     * @param bookingRef BDC reference number (for exception message)
     * @return the non-null decoded message body 
     * @throws IOException on I/O error or if message body not found
     */
    private String fetchMessageBody( Gmail gmail, String messageId, String bookingRef ) throws IOException {
        String messageBody = fetchMessageBody( gmail, messageId );
        if ( messageBody == null ) {
            throw new EmptyResultDataAccessException( "No messages body found for BDC " + bookingRef, 1 );
        }
        return messageBody;
    }

    /**
     * Returns the message body for the given message ID.
     * 
     * @param gmail the connected Gmail client
     * @param messageId the unique message ID to retrieve
     * @return the decoded message body or null if not found
     * @throws IOException on I/O error
     */
    private String fetchMessageBody(Gmail gmail, String messageId) throws IOException {
        Message fullMessage = gmail.users().messages().get(GMAIL_USER, messageId).setFormat("full").execute();
        LOGGER.debug( fullMessage.toPrettyString() );
        
        // return the first message part with a non-empty body
        // with a mime-type of text/plain or text/html
        if ( fullMessage.getPayload() != null && fullMessage.getPayload().getBody() != null 
                && fullMessage.getPayload().getBody().getData() != null ) {
            return webSafeBase64Decode( fullMessage.getPayload().getBody().getData() );
        }
        if ( fullMessage.getPayload() != null && fullMessage.getPayload().getParts() != null ) {
            for ( MessagePart part : fullMessage.getPayload().getParts() ) {
                if ( part.getParts() != null ) {
                    for ( MessagePart innerPart : part.getParts() ) {
                        if ( innerPart.getBody() != null
                                && ("text/plain".equals( innerPart.getMimeType() )
                                        || "text/html".equals( innerPart.getMimeType() )) ) {
                            return webSafeBase64Decode( innerPart.getBody().getData() );
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the content of the first matched group within the given text.
     * 
     * @param regex regex containing at least one matching group
     * @param contentToSearch the content to apply search to
     * @return the matched group
     * @throws EmptyResultDataAccessException if no match found
     */
    private String findAndReturn( String regex, String contentToSearch ) throws EmptyResultDataAccessException {
        Matcher m = Pattern.compile( regex, Pattern.MULTILINE ).matcher( contentToSearch );
        if ( m.find() ) {
            return m.group( 1 );
        }
        throw new EmptyResultDataAccessException( "Content not found for " + regex, 1 );
    }

    /**
     * Decodes a base-64 decoded string.
     * 
     * @param input base-64 string
     * @return non-null decoded string
     */
    private static String webSafeBase64Decode(String input) {
        // need to convert some characters first
        // http://stackoverflow.com/questions/24812139/base64-decoding-of-mime-email-not-working-gmail-api
        return input == null ? null : 
            new String( Base64.getDecoder().decode( input.replace( '-', '+' ).replace( '_', '/' ) ) );
    }

    /**
     * Returns the data store factory used to store user credentials for this application.
     * 
     * @return non-null directory name
     * @throws IOException on initialisation error
     */
    public DataStoreFactory getCredentialsDataStoreFactory() throws IOException {
        File dir = new File( userCredentialsDirectory );
        if( false == dir.exists() || false == dir.isDirectory() ) {
            throw new UnrecoverableFault( "Directory " + userCredentialsDirectory + " does not exist!" );
        }
        return new FileDataStoreFactory( dir );
    }

    /**
     * Returns the client secrets object used to access Gmail services.
     * 
     * @return non-null secrets
     * @throws IOException on initialisation error
     */
    public GoogleClientSecrets getGmailClientSecret() throws IOException {
        // Load client secrets.
        InputStream in = getClass().getResourceAsStream( oauthClientIdFile );
        GoogleClientSecrets clientSecrets = 
                GoogleClientSecrets.load( JSON_FACTORY, new InputStreamReader( in ) );
        return clientSecrets;
    }
}