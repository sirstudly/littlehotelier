package com.macbackpackers.jobs;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.EvlNightsBeyondFiveExportService;
import com.macbackpackers.services.EvlNightsBeyondFiveExportService.ExportResult;
import com.macbackpackers.services.GmailService;

/**
 * Builds the EVL nights-6+ room revenue Excel report and emails it as an attachment.
 * Mandatory job parameter: {@code email}.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.RunQuarterlyEvl6PlusNightsReportJob" )
public class RunQuarterlyEvl6PlusNightsReportJob extends AbstractJob {

    @Autowired
    @Transient
    private EvlNightsBeyondFiveExportService exportService;

    @Autowired
    @Transient
    private GmailService gmailService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Value( "${evl.enabled:false}" )
    @Transient
    private boolean evlEnabled;

    @Override
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            LOGGER.info( "Skipping EVL nights 6+ report (not Cloudbeds)" );
            return;
        }
        if ( false == evlEnabled ) {
            LOGGER.info( "Skipping EVL nights 6+ report (evl.enabled=false)" );
            return;
        }

        String email = getEmail();
        String property = exportService.resolvePropertyCode();
        String filename = "evl_nights6plus_" + property + ".xlsx";
        File tempFile = null;

        try ( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            ExportResult result = exportService.exportToTempFile( webClient, property );
            tempFile = result.getFile();

            String subject = "EVL nights 6+ room revenue (" + property + ")";
            String body = "<p>Attached is the EVL nights-6+ room revenue report for <b>"
                    + property.toUpperCase() + "</b>.</p>"
                    + "<p>Reservations: " + result.getRowCount()
                    + " (enrichment errors: " + result.getErrorCount() + ")<br/>"
                    + "Generated: "
                    + LocalDateTime.now().format( DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm" ) )
                    + "</p>";

            gmailService.sendEmail( email, null, subject, body, tempFile, filename );
            LOGGER.info( "Emailed EVL nights 6+ report for {} to {} (rows={})",
                    property, email, result.getRowCount() );
        }
        finally {
            if ( tempFile != null && tempFile.exists() && false == tempFile.delete() ) {
                LOGGER.warn( "Could not delete temp report file {}", tempFile.getAbsolutePath() );
            }
        }
    }

    public String getEmail() {
        String email = getParameter( "email" );
        if ( StringUtils.isBlank( email ) ) {
            throw new MissingUserDataException( "Missing or blank parameter email" );
        }
        return email.trim();
    }

    public void setEmail( String email ) {
        setParameter( "email", email );
    }
}
