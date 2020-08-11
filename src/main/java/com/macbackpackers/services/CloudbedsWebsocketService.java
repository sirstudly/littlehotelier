
package com.macbackpackers.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.gargoylesoftware.htmlunit.WebClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.scrapers.CloudbedsJsonRequestFactory;
import com.macbackpackers.scrapers.CloudbedsScraper;

@Service
public class CloudbedsWebsocketService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private CloudbedsScraper scraper;

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private ApplicationContext appContext;

    public WebSocketSession connect() throws Exception {
        WebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add( "Cookie", jsonRequestFactory.getCookies() );
        ListenableFuture<WebSocketSession> future = client.doHandshake(
                new CloudbedsWebSocketHandler(), headers, getCalendarWSSURI() );
        return future.get();
    }

    private URI getCalendarWSSURI() throws IOException, URISyntaxException {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            return scraper.getCalendarWSSURI( webClient );
        }
    }

    class CloudbedsWebSocketHandler extends AbstractWebSocketHandler {

        @Override
        public void afterConnectionEstablished( WebSocketSession session ) throws Exception {
            LOGGER.info( "afterConnectionEstablished: " + session );
            JsonObject pong = new JsonObject();
            // i think this is just a nanosecond value but not able to get that much precision here...
            String uniqueId = System.currentTimeMillis() + StringUtils.leftPad( String.valueOf( new Random().nextInt( 99999 ) ), 5, "0" );
            pong.addProperty( "action", "migrate" );
            pong.addProperty( "id", uniqueId );
            pong.addProperty( "__created", System.currentTimeMillis() );
            pong.addProperty( "announcementsLast", "" );
            pong.addProperty( "property_id", "17363" );
            pong.addProperty( "version", "https://front.cloudbeds.com/front/mfd-front--v9.0.0/app.js.gz" );
            LOGGER.info( "Sending initial message: " + pong.toString() );
            session.sendMessage( new TextMessage( pong.toString() ) );
        }

        @Override
        protected void handleTextMessage( WebSocketSession session, TextMessage message ) throws Exception {
            LOGGER.info( "handleTextMessage: " + session + " message: " + message.getPayload() );
            JsonObject payload = gson.fromJson( message.getPayload(), JsonObject.class );
            if ( payload.has( "guarantee" ) ) {
                LOGGER.info( "handleTextMessage: reflecting guarantee action" );
                JsonObject pong = new JsonObject();
                pong.addProperty( "action", "guarantee" );
                pong.addProperty( "guarantee", payload.get( "guarantee" ).getAsString() );
                pong.addProperty( "property_id", "17363" );
                pong.addProperty( "version", "https://front.cloudbeds.com/front/mfd-front--v9.0.0/app.js.gz" );
                session.sendMessage( new TextMessage( pong.toString() ) );
            }
        }

        @Override
        public void handleTransportError( WebSocketSession session, Throwable exception ) throws Exception {
            LOGGER.info( "handleTransportError: " + session + " th: " + exception );
        }

        @Override
        public void afterConnectionClosed( WebSocketSession session, CloseStatus status ) throws Exception {
            LOGGER.info( "afterConnectionClosed: " + session + " status: " + status );
        }
    }
}
