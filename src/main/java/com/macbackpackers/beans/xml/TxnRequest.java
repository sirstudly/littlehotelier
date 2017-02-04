
package com.macbackpackers.beans.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.builder.ToStringBuilder;

@XmlRootElement( name = "Txn" )
@XmlAccessorType( XmlAccessType.FIELD )
public class TxnRequest {

    @XmlElement( name = "PostUsername" )
    private String postUsername;

    @XmlElement( name = "PostPassword" )
    private String postPassword;

    @XmlElement( name = "CardHolderName" )
    private String cardHolderName;

    @XmlElement( name = "CardNumber" )
    private String cardNumber;

    @XmlElement( name = "Amount" )
    private String amount;

    @XmlElement( name = "DateExpiry" )
    private String dateExpiry;

    @XmlElement( name = "Cvc2" )
    private String cvc2;

    @XmlElement( name = "Cvc2Presence" )
    private Cvc2Presence cvc2Presence;

    @XmlElement( name = "InputCurrency" )
    private String inputCurrency;

    @XmlElement( name = "TxnType" )
    private String txnType;

    /** Unique 16 character ID for this transaction (used to query status afterwards) */
    @XmlElement( name = "TxnId" )
    private String txnId;

    /** Optional Reference to Appear on Transaction Reports Max 64 Characters */
    @XmlElement( name = "MerchantReference" )
    private String merchantReference;

    public String getPostUsername() {
        return postUsername;
    }

    public void setPostUsername( String postUsername ) {
        this.postUsername = postUsername;
    }

    public String getPostPassword() {
        return postPassword;
    }

    public void setPostPassword( String postPassword ) {
        this.postPassword = postPassword;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName( String cardHolderName ) {
        this.cardHolderName = cardHolderName;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber( String cardNumber ) {
        this.cardNumber = cardNumber;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount( String amount ) {
        this.amount = amount;
    }

    public String getDateExpiry() {
        return dateExpiry;
    }

    public void setDateExpiry( String dateExpiry ) {
        this.dateExpiry = dateExpiry;
    }

    public String getCvc2() {
        return cvc2;
    }

    public void setCvc2( String cvc2 ) {
        this.cvc2 = cvc2;
    }

    public Cvc2Presence getCvc2Presence() {
        return cvc2Presence;
    }

    public void setCvc2Presence( Cvc2Presence cvc2Presence ) {
        this.cvc2Presence = cvc2Presence;
    }

    public String getInputCurrency() {
        return inputCurrency;
    }

    public void setInputCurrency( String inputCurrency ) {
        this.inputCurrency = inputCurrency;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType( String txnType ) {
        this.txnType = txnType;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId( String txnId ) {
        this.txnId = txnId;
    }

    public String getMerchantReference() {
        return merchantReference;
    }

    public void setMerchantReference( String merchantReference ) {
        this.merchantReference = merchantReference;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
    
}
