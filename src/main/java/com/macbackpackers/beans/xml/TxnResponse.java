
package com.macbackpackers.beans.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

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
    
    /**
     * Returns the error text from the response code value.
     * Gleaned from the Interwebs; looks to be correct.
     * 
     * @return response code text (or null if not found)
     */
    public String getResponseCodeText() {
        if ( getResponseCode() == null ) {
            return null;
        }
        switch ( getResponseCode() ) {
            case "00":
                return "Transaction is approved and successful.";
            case "01":
                return "The transaction was not approved. Please contact the issuer of the credit card.";
            case "04":
                return "Refer To Card Issuer.";
            case "05":
                return "Card DECLINED on banks end.";
            case "14":
                return "Invalid Card Number.";
            case "51":
                return "Insufficient Funds.";
            case "54":
                return "Expired card or expiry date incorrect.";
            case "58":
                return "Function Not Permitted To Terminal.";
            case "65":
                return "Acceptor Contact Acquirer, Security.";
            case "76":
                return "Possible expiry date or pin problem. Not enough money on purchase.";
            case "AK":
                return "No BIN Range match found for card in port list / Auth not enabled on port.";
            case "AQ":
                //return "Amex is not enabled on the port.";
                return "Amex not enabled. Charge manually using EFTPOS terminal."; // overriding default msg
            case "AU":
                return "Discover not Accepted.";
            case "LR":
                return "Local Risk Management Declined.";
            case "NU":
                return "Local Fail (Reject Card Number).";
            case "QK":
                return "Invalid Card Number invalid length. If live, it could be invalid expiry date.";
            case "RC":
                return "3D Secure error/Fusion Cancellation/FEP Declined.";
            case "U9":
                return "Uplink Timeout.";
            case "Y7":
                return "Invalid Card Number (Incorrect Length).";
            case "YT":
                return "Invalid CVC2.";
        }
        return null;
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
