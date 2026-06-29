
package com.macbackpackers.beans.cloudbeds.responses;

import java.math.BigDecimal;

import com.google.gson.annotations.SerializedName;

/**
 * Corresponds with an entry on the "Folio" page for a booking.
 *
 */
public class TransactionRecord {

    private String id;
    private String reservationId;
    private String datetimeTransaction;
    private String description;
    private String descriptionFilter;
    private String notes;
    private String type;
    @SerializedName( "void" )
    private Object voidFlag;
    private Boolean canBeVoided;
    private String debit;
    private String credit;
    private String paid;
    private String transactionType;
    private String creditCardType;
    private String creditCardId;
    private String cardNumber;
    private String gatewayAuthorization;
    private String paymentId;
    private String paymentStatus;
    private String gatewayName;
    private String originalDescription;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getDatetimeTransaction() {
        return datetimeTransaction;
    }

    public void setDatetimeTransaction( String datetimeTransaction ) {
        this.datetimeTransaction = datetimeTransaction;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription( String description ) {
        this.description = description;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes( String notes ) {
        this.notes = notes;
    }

    public String getType() {
        return type;
    }

    public void setType( String type ) {
        this.type = type;
    }

    public Object getVoidFlag() {
        return voidFlag;
    }

    public void setVoidFlag( Object voidFlag ) {
        this.voidFlag = voidFlag;
    }

    public Boolean getCanBeVoided() {
        return canBeVoided;
    }

    public void setCanBeVoided( Boolean canBeVoided ) {
        this.canBeVoided = canBeVoided;
    }

    public boolean isVoided() {
        if ( voidFlag instanceof Boolean ) {
            return Boolean.TRUE.equals( voidFlag );
        }
        if ( voidFlag != null ) {
            String s = voidFlag.toString();
            return "1".equals( s ) || "true".equalsIgnoreCase( s );
        }
        return false;
    }

    public boolean isVoidable() {
        return Boolean.TRUE.equals( canBeVoided );
    }

    public String getDebit() {
        return debit;
    }

    public void setDebit( String debit ) {
        this.debit = debit;
    }

    public String getCredit() {
        return credit;
    }

    public void setCredit( String credit ) {
        this.credit = credit;
    }

    public BigDecimal getDebitAsBigDecimal() {
        return debit == null ? null : new BigDecimal(debit.replaceAll("£", ""));
    }

    public BigDecimal getPaidAsBigDecimal() {
        if ( paid == null || paid.trim().isEmpty() ) {
            return null;
        }
        return new BigDecimal( paid.replaceAll( "£", "" ) );
    }

    public void setPaid( String paid ) {
        this.paid = paid;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType( String transactionType ) {
        this.transactionType = transactionType;
    }

    public String getCreditCardType() {
        return creditCardType;
    }

    public void setCreditCardType( String creditCardType ) {
        this.creditCardType = creditCardType;
    }

    public String getCreditCardId() {
        return creditCardId;
    }

    public void setCreditCardId( String creditCardId ) {
        this.creditCardId = creditCardId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber( String cardNumber ) {
        this.cardNumber = cardNumber;
    }

    public String getGatewayAuthorization() {
        return gatewayAuthorization;
    }

    public void setGatewayAuthorization( String gatewayAuthorization ) {
        this.gatewayAuthorization = gatewayAuthorization;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId( String paymentId ) {
        this.paymentId = paymentId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus( String paymentStatus ) {
        this.paymentStatus = paymentStatus;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public void setGatewayName( String gatewayName ) {
        this.gatewayName = gatewayName;
    }

    public String getOriginalDescription() {
        return originalDescription;
    }

    public void setOriginalDescription( String originalDescription ) {
        this.originalDescription = originalDescription;
    }

}
