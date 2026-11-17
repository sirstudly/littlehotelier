package com.macbackpackers.ronbot.dto;

import java.util.List;

/**
 * Compact Gmail message summary for ronbot inbox search.
 */
public class GmailMessageDto {

    private String id;
    private String threadId;
    private String from;
    private String to;
    private String subject;
    private String date;
    private String snippet;
    private List<String> labelIds;
    private List<String> attachmentNames;
    /** Plain/HTML body when requested; truncated. */
    private String body;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId( String threadId ) {
        this.threadId = threadId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom( String from ) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo( String to ) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject( String subject ) {
        this.subject = subject;
    }

    public String getDate() {
        return date;
    }

    public void setDate( String date ) {
        this.date = date;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet( String snippet ) {
        this.snippet = snippet;
    }

    public List<String> getLabelIds() {
        return labelIds;
    }

    public void setLabelIds( List<String> labelIds ) {
        this.labelIds = labelIds;
    }

    public List<String> getAttachmentNames() {
        return attachmentNames;
    }

    public void setAttachmentNames( List<String> attachmentNames ) {
        this.attachmentNames = attachmentNames;
    }

    public String getBody() {
        return body;
    }

    public void setBody( String body ) {
        this.body = body;
    }
}
