package com.macbackpackers.dao;

import com.macbackpackers.beans.BlacklistEntry;
import com.macbackpackers.config.LittleHotelierConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class SharedDbDAOTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private SharedDbDAO dao;

    @Test
    public void testFetchBlacklistEntries() {
        List<BlacklistEntry> entries = dao.fetchBlacklistEntries();
        LOGGER.info( "Found {} entries", entries.size() );
        entries.forEach( e -> LOGGER.info( e.getEmail() + " -> " + e.getFirstName() + " " + e.getLastName() ) );
    }
}
