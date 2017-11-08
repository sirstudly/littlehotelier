
package com.macbackpackers.beans;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table( name = "wp_lh_job_param" )
public class JobParameter implements NameValuePair {

    @Id
    @GeneratedValue
    @Column( name = "job_param_id" )
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @Column( name = "name" )
    private String name;

    @Column( name = "value" )
    private String value;

    public JobParameter() {
        // default constructor
    }

    public JobParameter( Job job, String name, String value ) {
        this.job = job;
        this.name = name;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob( Job job ) {
        this.job = job;
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
