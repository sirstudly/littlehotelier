package com.macbackpackers.jobs;

import java.io.File;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.ChannelProductionReportService;
import com.macbackpackers.services.ChannelProductionReportService.MonthReport;
import com.macbackpackers.services.GmailService;

/**
 * Builds the Channel Production comparison workbook for {@code year_month} and the same month in
 * the two previous years, and emails it to each address in {@code to_emails} (comma-delimited).
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.RunChannelProductionReportJob" )
public class RunChannelProductionReportJob extends AbstractJob {

    private static final int YEARS_TO_REPORT = 3;

    @Autowired
    @Transient
    private ChannelProductionReportService reportService;

    @Autowired
    @Transient
    private GmailService gmailService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            LOGGER.info( "Skipping channel production report (not Cloudbeds)" );
            return;
        }

        YearMonth yearMonth = getYearMonth();
        List<String> toEmails = getToEmails();
        String property = reportService.resolvePropertyCode();
        String title = property.toUpperCase() + " Channel Report "
                + yearMonth.getMonth().getDisplayName( TextStyle.FULL, Locale.ENGLISH ) + " " + yearMonth.getYear();
        File tempFile = null;

        try ( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            List<MonthReport> reports = new ArrayList<>( YEARS_TO_REPORT );
            for ( int i = 0; i < YEARS_TO_REPORT; i++ ) {
                reports.add( reportService.fetchMonth( webClient, yearMonth.minusYears( i ) ) );
            }
            tempFile = reportService.writeWorkbook( property, reports );

            String body = "<p>Attached is the <b>" + title + "</b> (compared against the same month in the previous "
                    + ( YEARS_TO_REPORT - 1 ) + " years).</p>"
                    + "<p>Generated: "
                    + LocalDateTime.now().format( DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm" ) )
                    + "</p>";
            for ( String email : toEmails ) {
                gmailService.sendEmail( email, null, title, body, tempFile, title + ".xlsx" );
                LOGGER.info( "Emailed {} to {}", title, email );
            }
        }
        finally {
            if ( tempFile != null && tempFile.exists() && false == tempFile.delete() ) {
                LOGGER.warn( "Could not delete temp report file {}", tempFile.getAbsolutePath() );
            }
        }
    }

    public YearMonth getYearMonth() {
        String yearMonth = getParameter( "year_month" );
        if ( StringUtils.isBlank( yearMonth ) ) {
            throw new MissingUserDataException( "Missing or blank parameter year_month" );
        }
        try {
            return YearMonth.parse( yearMonth.trim() );
        }
        catch ( DateTimeParseException e ) {
            throw new MissingUserDataException( "Invalid year_month (expected YYYY-MM): " + yearMonth );
        }
    }

    public void setYearMonth( YearMonth yearMonth ) {
        setParameter( "year_month", yearMonth.toString() );
    }

    public List<String> getToEmails() {
        String toEmails = getParameter( "to_emails" );
        List<String> emails = toEmails == null ? List.of() : Arrays.stream( toEmails.split( "," ) )
                .map( String::trim )
                .filter( StringUtils::isNotBlank )
                .collect( Collectors.toList() );
        if ( emails.isEmpty() ) {
            throw new MissingUserDataException( "Missing or blank parameter to_emails" );
        }
        return emails;
    }

    public void setToEmails( String toEmails ) {
        setParameter( "to_emails", toEmails );
    }
}
