package com.macbackpackers.beans;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Uploaded Airbnb transactions CSV batch stored in MariaDB for the processor to parse.
 */
@Entity
@Table( name = "wp_lh_airbnb_import_batch" )
public class AirbnbImportBatch {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    @Column( name = "created_date", insertable = false, updatable = false )
    private Timestamp createdDate;

    @Column( name = "created_by" )
    private String createdBy;

    @Column( name = "filename", nullable = false )
    private String filename;

    @Column( name = "post_payment_field", nullable = false )
    private String postPaymentField = "amount";

    @Lob
    @Column( name = "csv_content", nullable = false, columnDefinition = "mediumtext" )
    private String csvContent;

    @Column( name = "parent_job_id" )
    private Long parentJobId;

    @Column( name = "status", nullable = false )
    private String status = "submitted";

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy( String createdBy ) {
        this.createdBy = createdBy;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename( String filename ) {
        this.filename = filename;
    }

    public String getPostPaymentField() {
        return postPaymentField;
    }

    public void setPostPaymentField( String postPaymentField ) {
        this.postPaymentField = postPaymentField;
    }

    public String getCsvContent() {
        return csvContent;
    }

    public void setCsvContent( String csvContent ) {
        this.csvContent = csvContent;
    }

    public Long getParentJobId() {
        return parentJobId;
    }

    public void setParentJobId( Long parentJobId ) {
        this.parentJobId = parentJobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }
}
