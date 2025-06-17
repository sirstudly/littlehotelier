
package com.macbackpackers.beans.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.builder.ToStringBuilder;

@XmlRootElement( name = "Transaction" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Transaction {

    // Authorisation code given back from the bank for that transaction
    @XmlElement( name = "AuthCode" )
    private String authCode;

    // 1 if transaction successful - 0 if declined or unsuccessful
    @XmlElement( name = "Authorized" )
    private String authorised;
    
    // (optional) Visa, MasterCard, etc.. 
    @XmlElement( name = "CardName" )
    private String cardName;

    // (masked) card number used
    @XmlElement( name = "CardNumber" )
    private String cardNumber;

    // CVC / CVV2 Result Code associated with the result of the CVC validation
    @XmlElement( name = "Cvc2ResultCode" )
    private String cvc2ResultCode;

    @XmlElement( name = "Amount" )
    private String amount;

    // Date transaction will be settled to Merchant Bank Account in YYYYMMDD format
    @XmlElement( name = "DateSettlement" )
    private String dateSettlement;

    // Required for refund and complete transactions
    @XmlElement( name = "DpsTxnRef" )
    private String dpsTxnRef;
    
    // 1 to indicate transaction flagged for retry
    @XmlElement( name = "Retry" )
    private String retry;
    
    // the UTC date of the transaction (YYYYMMDDHHMMSS
    @XmlElement( name = "RxDate" )
    private String rxDate;

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode( String authCode ) {
        this.authCode = authCode;
    }

    public String getAuthorised() {
        return authorised;
    }

    public void setAuthorised( String authorised ) {
        this.authorised = authorised;
    }

    public String getCardName() {
        return cardName;
    }

    public void setCardName( String cardName ) {
        this.cardName = cardName;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber( String cardNumber ) {
        this.cardNumber = cardNumber;
    }

    public String getCvc2ResultCode() {
        return cvc2ResultCode;
    }

    public void setCvc2ResultCode( String cvc2ResultCode ) {
        this.cvc2ResultCode = cvc2ResultCode;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount( String amount ) {
        this.amount = amount;
    }

    public String getDateSettlement() {
        return dateSettlement;
    }

    public void setDateSettlement( String dateSettlement ) {
        this.dateSettlement = dateSettlement;
    }

    public String getDpsTxnRef() {
        return dpsTxnRef;
    }

    public void setDpsTxnRef( String dpsTxnRef ) {
        this.dpsTxnRef = dpsTxnRef;
    }

    public String getRetry() {
        return retry;
    }

    public void setRetry( String retry ) {
        this.retry = retry;
    }

    public String getRxDate() {
        return rxDate;
    }

    public void setRxDate( String rxDate ) {
        this.rxDate = rxDate;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
    
}
