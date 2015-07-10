
package com.macbackpackers.beans;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table( name = "wp_lh_scheduled_job_param" )
public class ScheduledJobParameter implements NameValuePair {

    @Id
    @GeneratedValue
    @Column( name = "job_param_id" )
    private int id;

    @Column( name = "job_id" )
    private int jobId;

    @Column( name = "name" )
    private String name;

    @Column( name = "value" )
    private String value;

    public ScheduledJobParameter() {
        // default constructor
    }

    public ScheduledJobParameter( String name, String value ) {
        this.name = name;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId( int jobId ) {
        this.jobId = jobId;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    @Override
    public String getValue() {
        return value;
    }

    public void setValue( String value ) {
        this.value = value;
    }

    @Override
    public boolean equals( Object other ) {
        if ( other.getClass().isAssignableFrom( ScheduledJobParameter.class ) ) {
            return getName().equals( ((ScheduledJobParameter) other).getName() );
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
