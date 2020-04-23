
package com.macbackpackers.beans;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;

@Entity
@Table( name = "wp_stripe_tx_refund" )
@PrimaryKeyJoinColumn( name = "id" )
public class StripeRefund extends RefundTransaction {

    @Column( name = "cloudbeds_tx_id", nullable = false )
    private String cloudbedsTxId;

    @Column( name = "charge_id" )
    private String chargeId;

    @Column( name = "ref_response" )
    private String response;

    @Column( name = "ref_status" )
    private String status;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    public String getCloudbedsTxId() {
        return cloudbedsTxId;
    }

    public void setCloudbedsTxId( String cloudbedsTxId ) {
        this.cloudbedsTxId = cloudbedsTxId;
    }

    public String getChargeId() {
        return chargeId;
    }

    public void setChargeId( String chargeId ) {
        this.chargeId = chargeId;
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
