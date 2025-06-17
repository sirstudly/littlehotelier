
package com.macbackpackers.jobs;

import java.sql.Timestamp;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.SendEmailEntry;
import com.macbackpackers.services.GmailService;

/**
 * Job that sends all emails that haven't yet been sent.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendAllUnsentEmailJob" )
public class SendAllUnsentEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private GmailService gmailService;

    @Override
    public void processJob() throws Exception {
        for( SendEmailEntry emailJob : dao.fetchAllUnsentEmails() ) {
            // updating send date PRIOR to sending just in case we fail but still send the email
            // better not to spam our guests if there's a misconfiguration
            emailJob.setSendDate( new Timestamp( System.currentTimeMillis() ) );
            dao.saveSendEmailEntry( emailJob );
            gmailService.sendEmail(
                    emailJob.getEmail(),
                    emailJob.getFirstName() + " " + emailJob.getLastName(),
                    emailJob.getSendSubject(),
                    emailJob.getSendBody() );
        }
    }
}
