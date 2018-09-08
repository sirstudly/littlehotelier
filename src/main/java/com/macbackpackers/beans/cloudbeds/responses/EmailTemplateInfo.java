
package com.macbackpackers.beans.cloudbeds.responses;

/**
 * An email template.
 */
public class EmailTemplateInfo {

    private String id;
    private String emailType;
    private String designType;
    private String templateName;
    private String sendFromAddress;
    private String subject;
    private String emailBody;
    private String topImageId;
    private String topImageSrc;
    private String topImageAlign;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getEmailType() {
        return emailType;
    }

    public void setEmailType( String emailType ) {
        this.emailType = emailType;
    }

    public String getDesignType() {
        return designType;
    }

    public void setDesignType( String designType ) {
        this.designType = designType;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName( String templateName ) {
        this.templateName = templateName;
    }

    public String getSendFromAddress() {
        return sendFromAddress;
    }

    public void setSendFromAddress( String sendFromAddress ) {
        this.sendFromAddress = sendFromAddress;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject( String subject ) {
        this.subject = subject;
    }

    public String getEmailBody() {
        return emailBody;
    }

    public void setEmailBody( String emailBody ) {
        this.emailBody = emailBody;
    }

    public String getTopImageId() {
        return topImageId;
    }

    public void setTopImageId( String topImageId ) {
        this.topImageId = topImageId;
    }

    public String getTopImageSrc() {
        return topImageSrc;
    }

    public void setTopImageSrc( String topImageSrc ) {
        this.topImageSrc = topImageSrc;
    }

    public String getTopImageAlign() {
        return topImageAlign;
    }

    public void setTopImageAlign( String topImageAlign ) {
        this.topImageAlign = topImageAlign;
    }

}
