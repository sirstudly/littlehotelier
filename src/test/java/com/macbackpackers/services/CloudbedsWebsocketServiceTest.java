
package com.macbackpackers.services;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StreamUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class CloudbedsWebsocketServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    CloudbedsWebsocketService service;

    @Test
    public void testConnect() throws Exception {
        String json = StreamUtils.copyToString( getClass().getClassLoader().getResourceAsStream(
                "wss_block_beds.json" ), StandardCharsets.UTF_8 );
        
        WebSocketSession session = service.connect();
        LOGGER.info( "Session opened: " + session );
        session.sendMessage( new TextMessage(json) );
        Thread.sleep( 10000 );
    }

}
