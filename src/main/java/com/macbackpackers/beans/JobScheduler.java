
package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.apache.commons.lang3.time.FastDateFormat;
import org.hibernate.annotations.Type;

@Entity
@Table( name = "job_scheduler" )
public class JobScheduler {

    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "job_id", nullable = false )
    private int id;

    @Column( name = "classname" )
    private String classname;

    @Column( name = "repeat_time_minutes" )
    private Integer repeatTimeMinutes;

    @Column( name = "repeat_daily_at" )
    private String repeatDailyAt;

    @Column( name = "active_yn" )
    @Type( type = "yes_no" )
    private boolean active;

    @Column( name = "last_run_date" )
    private Timestamp lastRunDate;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    @OneToMany( cascade = { CascadeType.ALL }, fetch = FetchType.EAGER, mappedBy = "jobScheduler" )
    private Set<JobSchedulerParameter> parameters = new HashSet<JobSchedulerParameter>();

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getClassname() {
        return classname;
    }

    public void setClassname( String classname ) {
        this.classname = classname;
    }

    public Integer getRepeatTimeMinutes() {
        return repeatTimeMinutes;
    }

    public void setRepeatTimeMinutes( Integer repeatTimeMinutes ) {
        this.repeatTimeMinutes = repeatTimeMinutes;
    }

    public String getRepeatDailyAt() {
        return repeatDailyAt;
    }

    public void setRepeatDailyAt( String repeatDailyAt ) {
        this.repeatDailyAt = repeatDailyAt;
    }

    /**
     * Returns the hour portion from {@link #getRepeatDailyAt()}.
     * 
     * @return number between 0 and 23.
     */
    public int getRepeatDailyHour() {
        Matcher matchedHour = Pattern.compile( "([0-2][0-9]):" ).matcher( getRepeatDailyAt() );
        if ( matchedHour.find() ) {
            int hour = Integer.parseInt( matchedHour.group( 1 ) );
            if ( hour < 24 ) {
                return hour;
            }
        }
        throw new IllegalStateException( "Unable to determine hour: " + getRepeatDailyAt() );
    }

    /**
     * Returns the minute portion from {@link #getRepeatDailyAt()}.
     * 
     * @return number between 0 and 59.
     */
    public int getRepeatDailyMinute() {
        Matcher matchedMinute = Pattern.compile( "[0-2][0-9]:([0-5][0-9])" ).matcher( getRepeatDailyAt() );
        if ( matchedMinute.find() ) {
            return Integer.parseInt( matchedMinute.group( 1 ) );
        }
        throw new IllegalStateException( "Unable to determine minute: " + getRepeatDailyAt() );
    }

    public boolean isActive() {
        return active;
    }

    public void setActive( boolean active ) {
        this.active = active;
    }

    public Timestamp getLastRunDate() {
        return lastRunDate;
    }

    public void setLastRunDate( Timestamp lastRunDate ) {
        this.lastRunDate = lastRunDate;
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

    public Set<JobSchedulerParameter> getParameters() {
        return parameters;
    }

    public void setParameters( Set<JobSchedulerParameter> parameters ) {
        this.parameters = parameters;
    }

    /**
     * Convenience method for instantiating a Job for this ScheduledJob and setting its parameters.
     * 
     * @return non-null Job
     * @throws ReflectiveOperationException
     */
    public Job createNewJob() throws ReflectiveOperationException {

        // now create a Job with a copy of the parameters from the scheduled job (making substitutions when necessary)
        Job j = Job.class.cast( Class.forName( getClassname() ).newInstance() );
        j.setStatus( JobStatus.submitted );

        // now copy the parameters from the scheduled job to the actual job we're creating
        for ( JobSchedulerParameter param : getParameters() ) {

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

    /**
     * Returns whether this scheduled job is due to be run.
     * 
     * @return true if job needs to be run, false otherwise.
     */
    public boolean isOverdue() {

        if( getLastRunDate() == null ) {
            return true;
        }
        else if( null != getRepeatTimeMinutes() ) {
            return ChronoUnit.MINUTES.between( getLastRunDate().toInstant(), Instant.now() ) > getRepeatTimeMinutes();
        }
        else if( null != getRepeatDailyAt() ) {
            // if we're repeating daily, check if we've already passed the time today
            LocalDateTime repeatAt = LocalDateTime.now()
                        .withHour( getRepeatDailyHour() )
                        .withMinute( getRepeatDailyMinute() );
            
            // if we've passed this time and we haven't yet run it
            return LocalDateTime.now().isAfter( repeatAt ) 
                    && getLastRunDate().toLocalDateTime().isBefore( repeatAt );
        }
        throw new IllegalStateException( "Eh? Nothing to do!" );
    }
}
