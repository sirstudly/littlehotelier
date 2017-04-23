
package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table( name = "wp_lh_jobs" )
@Inheritance( strategy = InheritanceType.SINGLE_TABLE )
@DiscriminatorColumn( name = "classname", discriminatorType = DiscriminatorType.STRING )
public class Job {

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "job_id", nullable = false )
    private int id;

    @Column( name = "status", nullable = false )
    @Enumerated( EnumType.STRING )
    private JobStatus status;

    @Column( name = "start_date" )
    private Timestamp jobStartDate;

    @Column( name = "end_date" )
    private Timestamp jobEndDate;

    @Column( name = "processed_by" )
    private String processedBy;

    @Column( name = "created_date" )
    private Timestamp createdDate = new Timestamp( System.currentTimeMillis() );

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    @OneToMany( cascade = { CascadeType.ALL }, fetch = FetchType.EAGER, mappedBy = "job" )
    private Set<JobParameter> parameters = new HashSet<JobParameter>();

    @OneToMany( cascade = { CascadeType.ALL }, fetch = FetchType.LAZY )
    @JoinTable( name = "wp_lh_job_dependency", joinColumns = @JoinColumn( name = "job_id" ), inverseJoinColumns = @JoinColumn( name = "depends_on_job_id" ) )
    private Set<Job> dependentJobs = new HashSet<Job>();

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus( JobStatus status ) {
        this.status = status;
    }

    public Set<JobParameter> getParameters() {
        return parameters;
    }

    public void setParameters( Set<JobParameter> parameters ) {
        this.parameters = parameters;
    }

    public String getParameter( String name ) {
        for ( JobParameter param : getParameters() ) {
            if ( param.getName().equals( name ) ) {
                return param.getValue();
            }
        }
        return null;
    }

    public void setParameter( String name, String value ) {
        getParameters().add( new JobParameter( this, name, value ) );
    }

    public Timestamp getJobStartDate() {
        return jobStartDate;
    }

    public void setJobStartDate( Timestamp jobStartDate ) {
        this.jobStartDate = jobStartDate;
    }

    public Timestamp getJobEndDate() {
        return jobEndDate;
    }

    public void setJobEndDate( Timestamp jobEndDate ) {
        this.jobEndDate = jobEndDate;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy( String processedBy ) {
        this.processedBy = processedBy;
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

    public Set<Job> getDependentJobs() {
        return dependentJobs;
    }

    public void setDependentJobs( Set<Job> dependentJobs ) {
        this.dependentJobs = dependentJobs;
    }

}
