
package com.macbackpackers.beans.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

@XmlRootElement( name = "Txn" )
@XmlAccessorType( XmlAccessType.FIELD )
public class TxnResponse {

    @XmlElement( name = "Transaction" )
    private Transaction transaction;

    // A more detailed explanation of the response from the bank
    @XmlElement( name = "HelpText" )
    private String helpText;

    // 2 character response code
    @XmlElement( name = "ReCo" )
    private String responseCode;

    @XmlElement( name = "ResponseText" )
    private String responseText;

    // 1 if the result of the transaction could not be determined.
    @XmlElement( name = "StatusRequired" )
    private String statusRequired;

    // 1 if transaction successful - 0 if declined or unsuccessful
    @XmlElement( name = "Success" )
    private String success;

    // I think this is the TxnId from the original request
    @XmlElement( name = "TxnRef" )
    private String txnRef;

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText( String helpText ) {
        this.helpText = helpText;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode( String responseCode ) {
        this.responseCode = responseCode;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText( String responseText ) {
        this.responseText = responseText;
    }

    public String getStatusRequired() {
        return statusRequired;
    }

    public void setStatusRequired( String statusRequired ) {
        this.statusRequired = statusRequired;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef( String txnRef ) {
        this.txnRef = txnRef;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction( Transaction transaction ) {
        this.transaction = transaction;
    }

    public String getSuccess() {
        return success;
    }

    public void setSuccess( String success ) {
        this.success = success;
    }
    
    /**
     * Convenience method for checking whether the transaction was successful.
     * 
     * @return true if successful, false if not executed or failed
     */
    public boolean isSuccessful() {
        return "1".equals( StringUtils.trim( getSuccess() ) );
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }

}
