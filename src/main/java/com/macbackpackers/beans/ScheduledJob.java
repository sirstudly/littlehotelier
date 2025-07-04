
package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.apache.commons.lang3.time.FastDateFormat;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.type.YesNoConverter;

/**
 * A scheduled job will merely insert a "submitted" record into the job table. The ProcessorService
 * will then pick up the job in its own due time. It is not the task of a ScheduledJob to actually
 * "run" the job.
 *
 */
@Entity
@Table( name = "wp_lh_scheduled_jobs" )
public class ScheduledJob {

    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "job_id", nullable = false )
    private int id;

    @Column( name = "classname", nullable = false )
    private String classname;

    @Column( name = "cron_schedule", nullable = false )
    private String cronSchedule;

    @Column( name = "active_yn" )
    @Convert(converter = YesNoConverter.class)
    private boolean active;

    @Column( name = "last_scheduled_date" )
    private Timestamp lastScheduledDate;

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    @OneToMany( cascade = jakarta.persistence.CascadeType.ALL, fetch = FetchType.EAGER )
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

    /**
     * Convenience method for instantiating a Job for this ScheduledJob and setting its parameters.
     * 
     * @return non-null Job
     * @throws ReflectiveOperationException
     */
    public Job createNewJob() throws ReflectiveOperationException {

        // now create a Job with a copy of the parameters from the scheduled job (making substitutions when necessary)
        Job j = (Job) Class.forName( getClassname() ).getDeclaredConstructor().newInstance();
        j.setStatus( JobStatus.submitted );

        // now copy the parameters from the scheduled job to the actual job we're creating
        for ( ScheduledJobParameter param : getParameters() ) {

            Pattern p = Pattern.compile( "TODAY([\\+\\-][0-9]+)$" );
            Matcher m = p.matcher( param.getValue() );

            // auto-fill TODAY with today's date
            if ( "TODAY".equals( param.getValue() ) ) {
                j.setParameter( param.getName(), DATE_FORMAT_YYYY_MM_DD.format( new Date() ) );
            }
            // auto-fill TODAY(+/- adjustment value)
            else if ( m.find() ) {
                String matchedAdjustment = m.group( 1 );
                Calendar cal = Calendar.getInstance();
                cal.add( Calendar.DATE, Integer.parseInt( matchedAdjustment ) );
                j.setParameter( param.getName(), DATE_FORMAT_YYYY_MM_DD.format( cal.getTime() ) );
            }
            else {
                j.setParameter( param.getName(), param.getValue() );
            }
        }
        return j;
    }
}
