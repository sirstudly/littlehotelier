package com.macbackpackers.beans;

import java.sql.Timestamp;

public class Job {

    private int id;
    private String name;
    private JobStatus status;
    private Timestamp createdDate;
    private Timestamp lastUpdatedDate;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus( JobStatus status ) {
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

    public void setLastUpdatedDate( Timestamp lasterUpdatedDate ) {
        this.lastUpdatedDate = lasterUpdatedDate;
    }

}
