package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.util.Properties;

public class Job {

    private int id;
    private String className;
    private JobStatus status;
    private Timestamp createdDate;
    private Timestamp lastUpdatedDate;
    private Properties parameters = new Properties();

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName( String className ) {
        this.className = className;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus( JobStatus status ) {
        this.status = status;
    }
    
    public Properties getParameters() {
        return parameters;
    }

    public void setParameters( Properties parameters ) {
        this.parameters = parameters;
    }

    public String getParameter( String name ) {
        return getParameters().getProperty( name );
    }
    
    public void setParameter( String name, String value ) {
        getParameters().setProperty( name, value );
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
