package com.macbackpackers.beans.cloudbeds.responses;

public class AddPaymentResponseData {
    private String status;
    private Boolean requiresAuthentication;
    private String gatewayAuthorization;
    private String paidValue;
    private String currency;
    private String transactionType;
    private String description;

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public Boolean getRequiresAuthentication() {
        return requiresAuthentication;
    }

    public void setRequiresAuthentication( Boolean requiresAuthentication ) {
        this.requiresAuthentication = requiresAuthentication;
    }

    public String getGatewayAuthorization() {
        return gatewayAuthorization;
    }

    public void setGatewayAuthorization( String gatewayAuthorization ) {
        this.gatewayAuthorization = gatewayAuthorization;
    }

    public String getPaidValue() {
        return paidValue;
    }

    public void setPaidValue( String paidValue ) {
        this.paidValue = paidValue;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency( String currency ) {
        this.currency = currency;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType( String transactionType ) {
        this.transactionType = transactionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription( String description ) {
        this.description = description;
    }
}
