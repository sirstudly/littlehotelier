package com.macbackpackers.ronbot.dto;

import java.util.List;

/**
 * Result of a hostel inbox Gmail search for ronbot.
 */
public class GmailSearchDto {

    private String property;
    private String query;
    private int count;
    private List<GmailMessageDto> messages;

    public String getProperty() {
        return property;
    }

    public void setProperty( String property ) {
        this.property = property;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery( String query ) {
        this.query = query;
    }

    public int getCount() {
        return count;
    }

    public void setCount( int count ) {
        this.count = count;
    }

    public List<GmailMessageDto> getMessages() {
        return messages;
    }

    public void setMessages( List<GmailMessageDto> messages ) {
        this.messages = messages;
    }
}
