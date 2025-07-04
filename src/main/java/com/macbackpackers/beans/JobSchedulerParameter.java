
package com.macbackpackers.beans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table( name = "job_scheduler_param" )
public class JobSchedulerParameter implements NameValuePair {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "job_param_id" )
    private int id;

    @ManyToOne( fetch = FetchType.LAZY )
    @JoinColumn( name = "job_id" )
    private JobScheduler jobScheduler;

    @Column( name = "name" )
    private String name;

    @Column( name = "value" )
    private String value;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public JobScheduler getJobScheduler() {
        return jobScheduler;
    }

    public void setJobScheduler( JobScheduler jobScheduler ) {
        this.jobScheduler = jobScheduler;
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
        if ( other.getClass().isAssignableFrom( JobParameter.class ) ) {
            return getName().equals( ((JobParameter) other).getName() );
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
