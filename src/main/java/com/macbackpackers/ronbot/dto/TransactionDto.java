package com.macbackpackers.ronbot.dto;

/**
 * Redacted folio transaction for ronbot responses.
 */
public class TransactionDto {

    private String id;
    private String reservationId;
    private String datetimeTransaction;
    private String description;
    private String notes;
    private String type;
    private Object voidFlag;
    private Boolean canBeVoided;
    private String debit;
    private String credit;
    private String paid;
    private String transactionType;
    private String creditCardType;
    /** Masked / last4 only — never full PAN. */
    private String cardNumberLast4;
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

    public void setReservationId( String reservationId ) {
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

    public String getPaid() {
        return paid;
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

    public String getCardNumberLast4() {
        return cardNumberLast4;
    }

    public void setCardNumberLast4( String cardNumberLast4 ) {
        this.cardNumberLast4 = cardNumberLast4;
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
