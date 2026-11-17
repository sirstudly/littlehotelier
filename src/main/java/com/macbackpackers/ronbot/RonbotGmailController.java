package com.macbackpackers.ronbot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.macbackpackers.ronbot.dto.GmailMessageDto;
import com.macbackpackers.ronbot.dto.GmailSearchDto;
import com.macbackpackers.services.GmailService;

/**
 * Hostel reception inbox search via each property's Gmail OAuth credentials.
 */
@Profile( "ronbot-parent" )
@RestController
@RequestMapping( "/ronbot" )
public class RonbotGmailController {

    private static final int DEFAULT_MAX_RESULTS = 10;

    private final PropertyContextRegistry propertyContexts;

    public RonbotGmailController( PropertyContextRegistry propertyContexts ) {
        this.propertyContexts = propertyContexts;
    }

    /**
     * Search a property's Gmail mailbox. Pass raw {@code q} and/or structured filters
     * (AND-combined into a Gmail query), or {@code messageId} to fetch one message.
     */
    @GetMapping( "/{property}/gmail/search" )
    public GmailSearchDto search(
            @PathVariable String property,
            @RequestParam( name = "q", required = false ) String q,
            @RequestParam( name = "from", required = false ) String from,
            @RequestParam( name = "to", required = false ) String to,
            @RequestParam( name = "subject", required = false ) String subject,
            @RequestParam( name = "body", required = false ) String body,
            @RequestParam( name = "after", required = false ) String after,
            @RequestParam( name = "before", required = false ) String before,
            @RequestParam( name = "label", required = false ) String label,
            @RequestParam( name = "hasAttachment", required = false ) Boolean hasAttachment,
            @RequestParam( name = "messageId", required = false ) String messageId,
            @RequestParam( name = "maxResults", required = false ) Integer maxResults,
            @RequestParam( name = "includeBody", required = false, defaultValue = "false" ) boolean includeBody )
            throws IOException {

        String key = property == null ? "" : property.trim().toLowerCase();
        GmailService gmail = propertyContexts.require( key ).getBean( GmailService.class );

        String builtQuery = buildGmailQuery( q, from, to, subject, body, after, before, label, hasAttachment );
        if ( StringUtils.isBlank( messageId ) && StringUtils.isBlank( builtQuery ) ) {
            throw new IllegalArgumentException(
                    "Provide q and/or from/to/subject/body/after/before/label/hasAttachment, or messageId" );
        }

        int limit = maxResults == null ? DEFAULT_MAX_RESULTS : maxResults;
        List<GmailMessageDto> messages = gmail.searchMessages( builtQuery, limit, includeBody, messageId );

        GmailSearchDto dto = new GmailSearchDto();
        dto.setProperty( key );
        dto.setQuery( StringUtils.isNotBlank( messageId ) ? "id:" + messageId.trim() : builtQuery );
        dto.setCount( messages.size() );
        dto.setMessages( messages );
        return dto;
    }

    /**
     * AND-combines a raw {@code q} with structured Gmail operators.
     */
    static String buildGmailQuery(
            String q,
            String from,
            String to,
            String subject,
            String body,
            String after,
            String before,
            String label,
            Boolean hasAttachment ) {
        List<String> parts = new ArrayList<>();
        if ( StringUtils.isNotBlank( q ) ) {
            parts.add( q.trim() );
        }
        addOperator( parts, "from", from );
        addOperator( parts, "to", to );
        addOperator( parts, "subject", subject );
        if ( StringUtils.isNotBlank( body ) ) {
            // Gmail free-text matches body (and elsewhere); quote multi-word phrases
            parts.add( quoteIfNeeded( body.trim() ) );
        }
        addBareOperator( parts, "after", after );
        addBareOperator( parts, "before", before );
        if ( StringUtils.isNotBlank( label ) ) {
            String trimmed = label.trim();
            // INBOX/SENT etc. use label: or in: — prefer label: for custom, in: for system
            if ( trimmed.equalsIgnoreCase( "INBOX" ) || trimmed.equalsIgnoreCase( "SENT" )
                    || trimmed.equalsIgnoreCase( "TRASH" ) || trimmed.equalsIgnoreCase( "SPAM" )
                    || trimmed.equalsIgnoreCase( "DRAFT" ) || trimmed.equalsIgnoreCase( "STARRED" )
                    || trimmed.equalsIgnoreCase( "UNREAD" ) || trimmed.equalsIgnoreCase( "IMPORTANT" ) ) {
                parts.add( "in:" + trimmed.toLowerCase() );
            }
            else {
                parts.add( "label:" + quoteIfNeeded( trimmed ) );
            }
        }
        if ( Boolean.TRUE.equals( hasAttachment ) ) {
            parts.add( "has:attachment" );
        }
        return String.join( " ", parts );
    }

    private static void addOperator( List<String> parts, String name, String value ) {
        if ( StringUtils.isBlank( value ) ) {
            return;
        }
        parts.add( name + ":" + quoteIfNeeded( value.trim() ) );
    }

    /** after:/before: take YYYY/MM/DD or YYYY-MM-DD without quotes. */
    private static void addBareOperator( List<String> parts, String name, String value ) {
        if ( StringUtils.isBlank( value ) ) {
            return;
        }
        String normalized = value.trim().replace( '-', '/' );
        parts.add( name + ":" + normalized );
    }

    private static String quoteIfNeeded( String value ) {
        if ( value.indexOf( ' ' ) >= 0 || value.indexOf( '"' ) >= 0 ) {
            return "\"" + value.replace( "\"", "\\\"" ) + "\"";
        }
        return value;
    }

    @ExceptionHandler( IllegalArgumentException.class )
    public ResponseEntity<Map<String, String>> badRequest( IllegalArgumentException ex ) {
        return ResponseEntity.badRequest().body( Map.of( "error", ex.getMessage() ) );
    }

    @ExceptionHandler( IOException.class )
    public ResponseEntity<Map<String, String>> io( IOException ex ) {
        return ResponseEntity.status( HttpStatus.BAD_GATEWAY )
                .body( Map.of( "error", "Gmail request failed: " + ex.getMessage() ) );
    }
}
