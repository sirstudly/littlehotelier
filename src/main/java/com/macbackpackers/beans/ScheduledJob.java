
package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Type;

/**
 * A scheduled job will merely insert a "submitted" record into the job table. The ProcessorService
 * will then pick up the job in its own due time. It is not the task of a ScheduledJob to actually
 * "run" the job.
 *
 */
@Entity
@Table( name = "wp_lh_scheduled_jobs" )
public class ScheduledJob {

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "job_id", nullable = false )
    private int id;

    @Column( name = "classname", nullable = false )
    private String classname;

    @Column( name = "cron_schedule", nullable = false )
    private String cronSchedule;

    @Column( name = "active_yn" )
    @Type( type = "yes_no" )
    private boolean active;

    @Column( name = "last_scheduled_date" )
    private Timestamp lastScheduledDate;

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    @OneToMany( cascade = javax.persistence.CascadeType.ALL, fetch = FetchType.EAGER )
    @JoinColumn( name = "job_id" )
    @Cascade( { CascadeType.SAVE_UPDATE, CascadeType.DELETE } )
    private Set<ScheduledJobParameter> parameters = new HashSet<ScheduledJobParameter>();

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public Set<ScheduledJobParameter> getParameters() {
        return parameters;
    }

    public void setParameters( Set<ScheduledJobParameter> parameters ) {
        this.parameters = parameters;
    }

    public String getParameter( String name ) {
        for ( ScheduledJobParameter param : getParameters() ) {
            if ( param.getName().equals( name ) ) {
                return param.getValue();
            }
        }
        return null;
    }

    public void setParameter( String name, String value ) {
        getParameters().add( new ScheduledJobParameter( name, value ) );
    }

    public String getClassname() {
        return classname;
    }

    public void setClassname( String classname ) {
        this.classname = classname;
    }

    public String getCronSchedule() {
        return cronSchedule;
    }

    public void setCronSchedule( String cronSchedule ) {
        this.cronSchedule = cronSchedule;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive( boolean active ) {
        this.active = active;
    }

    public Timestamp getLastScheduledDate() {
        return lastScheduledDate;
    }

    public void setLastScheduledDate( Timestamp lastScheduledDate ) {
        this.lastScheduledDate = lastScheduledDate;
    }

    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate( Timestamp lasterUpdatedDate ) {
        this.lastUpdatedDate = lasterUpdatedDate;
    }

}
