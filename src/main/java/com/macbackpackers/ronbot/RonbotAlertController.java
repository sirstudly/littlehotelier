package com.macbackpackers.ronbot;

import java.util.Map;

import javax.mail.MessagingException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.macbackpackers.services.GmailService;

/**
 * Operational alerts (e.g. WhatsApp session down) sent from a property's Gmail account.
 * Only the property whose Gmail credentials are mounted at {@code /app/credentials/gmail}
 * in the read-api container can send.
 */
@Profile( "ronbot-parent" )
@RestController
@RequestMapping( "/ronbot" )
public class RonbotAlertController {

    private static final Logger LOGGER = LoggerFactory.getLogger( RonbotAlertController.class );

    private final PropertyContextRegistry propertyContexts;

    public RonbotAlertController( PropertyContextRegistry propertyContexts ) {
        this.propertyContexts = propertyContexts;
    }

    public static class AlertEmailRequest {
        /** Recipient; blank sends to the property's own Gmail inbox. */
        public String to;
        public String subject;
        /** HTML body. */
        public String body;
    }

    @PostMapping( "/{property}/alerts/email" )
    public ResponseEntity<Map<String, String>> sendAlertEmail(
            @PathVariable String property,
            @RequestBody AlertEmailRequest request ) {
        if ( request == null || StringUtils.isBlank( request.subject ) || StringUtils.isBlank( request.body ) ) {
            return ResponseEntity.badRequest().body( Map.of( "error", "subject and body are required" ) );
        }
        GmailService gmail;
        try {
            gmail = propertyContexts.require( property ).getBean( GmailService.class );
        }
        catch ( IllegalArgumentException ex ) {
            return ResponseEntity.badRequest().body( Map.of( "error", ex.getMessage() ) );
        }
        try {
            if ( StringUtils.isBlank( request.to ) ) {
                gmail.sendEmailToSelf( request.subject, request.body );
            }
            else {
                gmail.sendEmail( request.to.trim(), null, request.subject, request.body );
            }
            LOGGER.info( "Sent alert email via {} to {}: {}", property,
                    StringUtils.defaultIfBlank( request.to, "self" ), request.subject );
            return ResponseEntity.ok( Map.of( "status", "sent" ) );
        }
        catch ( MessagingException ex ) {
            return ResponseEntity.badRequest().body( Map.of( "error", ex.getMessage() ) );
        }
        catch ( Exception ex ) {
            LOGGER.error( "Failed to send alert email via {}", property, ex );
            return ResponseEntity.status( HttpStatus.BAD_GATEWAY )
                    .body( Map.of( "error", "Gmail send failed: " + ex.getMessage() ) );
        }
    }
}
