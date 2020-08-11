
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

import com.macbackpackers.services.CloudbedsWebsocketService;

/**
 * WSS Testing.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ConnectToCalendarWSSJob" )
public class ConnectToCalendarWSSJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsWebsocketService service;

    @Override
    public void processJob() throws Exception {
        WebSocketSession session = service.connect();
        LOGGER.info( "Session opened: " + session );
        Thread.sleep( 20000 );
    }

}
