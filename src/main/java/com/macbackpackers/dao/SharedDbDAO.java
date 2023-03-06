package com.macbackpackers.dao;

import com.macbackpackers.beans.BlacklistEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SharedDbDAO {

    @Autowired
    @Qualifier( "sharedJdbcTemplate" )
    private JdbcTemplate jdbcTemplate;

    public List<BlacklistEntry> fetchBlacklistEntries() {
        return jdbcTemplate.query("SELECT email, first_name, last_name from hbo_blacklist",
                new BeanPropertyRowMapper( BlacklistEntry.class ));
    }
}
