
package com.macbackpackers.beans;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;

@Entity
@Table( name = "wp_sagepay_tx_refund" )
@PrimaryKeyJoinColumn( name = "id" )
public class SagepayRefund extends RefundTransaction {

    @Column( name = "auth_vendor_tx_code", nullable = false )
    private String authVendorTxCode;

    @Column( name = "ref_vendor_tx_code" )
    private String vendorTxCode;

    @Column( name = "ref_response" )
    private String response;

    @Column( name = "ref_status" )
    private String status;

    @Column( name = "refund_status_detail" )
    private String statusDetail;

    @Column( name = "tx_id" )
    private String transactionId;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    public String getAuthVendorTxCode() {
        return authVendorTxCode;
    }

    public void setAuthVendorTxCode( String authVendorTxCode ) {
        this.authVendorTxCode = authVendorTxCode;
    }

    public String getVendorTxCode() {
        return vendorTxCode;
    }

    public void setVendorTxCode( String vendorTxCode ) {
        this.vendorTxCode = vendorTxCode;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse( String response ) {
        this.response = response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public void setStatusDetail( String statusDetail ) {
        this.statusDetail = statusDetail;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId( String transactionId ) {
        this.transactionId = transactionId;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }

    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate( Timestamp lastUpdatedDate ) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

}
