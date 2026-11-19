package com.macbackpackers.jobs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.AirbnbPayment;
import com.macbackpackers.beans.AirbnbPayoutEmailLine;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.ronbot.dto.GmailMessageDto;
import com.macbackpackers.services.AirbnbPaymentReconcileService;
import com.macbackpackers.services.AirbnbPayoutEmailParser;
import com.macbackpackers.services.AirbnbPayoutEmailParser.EmailTotals;
import com.macbackpackers.services.AirbnbPayoutEmailParser.LineItem;
import com.macbackpackers.services.AirbnbPayoutEmailParser.ParseResult;
import com.macbackpackers.services.GmailService;

/**
 * Polls the hostel Gmail mailbox for Airbnb bank-payout and cancel-notice emails.
 * <ul>
 * <li>Payout emails update {@link AirbnbPayment} from reservation/resolution line items
 * (provisional truth) and enqueue {@link AirbnbImportTransactionJob}.</li>
 * <li>Cancel-notice emails mark the confirmation code {@code needs_review}.</li>
 * </ul>
 * No-op when {@code hbo_airbnb_post_payment_field} is {@code gross_earnings}.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.PollAirbnbPayoutEmailsJob" )
public class PollAirbnbPayoutEmailsJob extends AbstractJob {

    private static final BigDecimal TOLERANCE = new BigDecimal( "0.10" );

    @Autowired
    @Transient
    private GmailService gmailService;

    @Override
    public void processJob() throws Exception {
        if ( dao.isAirbnbPostPaymentFieldGrossEarnings() ) {
            LOGGER.info( "Skipping PollAirbnbPayoutEmailsJob "
                    + "(hbo_airbnb_post_payment_field is gross_earnings)" );
            return;
        }

        Set<String> touchedCodes = new LinkedHashSet<>();
        int processedPayoutEmails = processPayoutEmails( touchedCodes );
        int processedCancelEmails = processCancelEmails();

        int enqueued = 0;
        for ( String code : touchedCodes ) {
            dao.upsertAirbnbPaymentFromPayoutEmail( code );
            AirbnbPayment payment = dao.fetchAirbnbPaymentByConfirmationCode( code );
            if ( payment == null ) {
                continue;
            }
            EmailTotals totals = dao.fetchAirbnbPayoutEmailTotals( code );
            if ( totals == null ) {
                continue;
            }
            if ( shouldSkipEnqueue( payment, totals ) ) {
                continue;
            }
            if ( dao.hasPendingAirbnbImportTransactionJob( code ) ) {
                LOGGER.info( "Skipping enqueue for {}: pending AirbnbImportTransactionJob", code );
                continue;
            }

            String action = totals.hasResolution()
                    ? AirbnbPaymentReconcileService.ACTION_AUTO
                    : AirbnbPaymentReconcileService.ACTION_POST_AMOUNT;

            payment.setProcessingStatus( AirbnbPayment.STATUS_PROCESSING );
            payment.setStatusReason( null );
            dao.updateAirbnbPayment( payment );

            AirbnbImportTransactionJob child = new AirbnbImportTransactionJob();
            child.setStatus( JobStatus.submitted );
            child.setConfirmationCode( code );
            child.setAction( action );
            int childId = dao.insertJob( child );
            payment.setLastJobId( Long.valueOf( childId ) );
            dao.updateAirbnbPayment( payment );
            enqueued++;
            LOGGER.info( "Enqueued AirbnbImportTransactionJob {} for {} action={} (net {})",
                    childId, code, action, totals.getNetAmount() );
        }

        LOGGER.info( "Airbnb email poll done: {} payout email(s), {} cancel email(s), {} child job(s)",
                processedPayoutEmails, processedCancelEmails, enqueued );
    }

    private int processPayoutEmails( Set<String> touchedCodes ) throws Exception {
        String query = "from:automated@airbnb.com in:anywhere subject:\"We sent a payout\" newer_than:"
                + getDaysBack() + "d";
        List<GmailMessageDto> messages = gmailService.searchMessages( query, 50, false, null );
        LOGGER.info( "Airbnb payout email poll: {} candidate(s) for q={}", messages.size(), query );

        int processed = 0;
        for ( GmailMessageDto msg : messages ) {
            if ( msg == null || StringUtils.isBlank( msg.getId() ) ) {
                continue;
            }
            if ( dao.isAirbnbPayoutEmailProcessed( msg.getId() ) ) {
                continue;
            }
            if ( false == AirbnbPayoutEmailParser.isPayoutSubject( msg.getSubject() ) ) {
                dao.insertAirbnbPayoutEmail( msg.getId(), msg.getSubject(), List.of() );
                continue;
            }

            String body = gmailService.fetchFullMessageBody( msg.getId() );
            ParseResult parsed = AirbnbPayoutEmailParser.parse( msg.getSubject(), body );
            List<AirbnbPayoutEmailLine> lines = new ArrayList<>();
            for ( LineItem item : parsed.getLines() ) {
                AirbnbPayoutEmailLine line = new AirbnbPayoutEmailLine();
                line.setConfirmationCode( item.getConfirmationCode() );
                line.setAmount( item.getAmount() );
                line.setGuestName( item.getGuestName() );
                line.setLineType( item.getLineType() );
                line.setSupersededYn( "N" );
                lines.add( line );
                touchedCodes.add( item.getConfirmationCode() );
            }

            if ( lines.isEmpty() ) {
                LOGGER.warn( "Payout email {} ({}) parsed with 0 lines; not marking processed",
                        msg.getId(), msg.getSubject() );
                continue;
            }

            Long emailId = dao.insertAirbnbPayoutEmail( msg.getId(), msg.getSubject(), lines );
            if ( emailId == null ) {
                continue;
            }
            processed++;
        }
        return processed;
    }

    private int processCancelEmails() throws Exception {
        String query = "from:automated@airbnb.com in:anywhere "
                + "(subject:\"Canceled: Reservation\" OR subject:\"Reservation canceled\") newer_than:"
                + getDaysBack() + "d";
        List<GmailMessageDto> messages = gmailService.searchMessages( query, 50, false, null );
        LOGGER.info( "Airbnb cancel email poll: {} candidate(s) for q={}", messages.size(), query );

        int processed = 0;
        for ( GmailMessageDto msg : messages ) {
            if ( msg == null || StringUtils.isBlank( msg.getId() ) ) {
                continue;
            }
            if ( dao.isAirbnbPayoutEmailProcessed( msg.getId() ) ) {
                continue;
            }
            String code = AirbnbPayoutEmailParser.parseCancelConfirmationCode( msg.getSubject() );
            // Always record message id so we do not refetch forever
            Long emailId = dao.insertAirbnbPayoutEmail( msg.getId(), msg.getSubject(), List.of() );
            if ( emailId == null ) {
                continue;
            }
            processed++;
            if ( StringUtils.isBlank( code ) ) {
                LOGGER.info( "Cancel email {} has no confirmation code in subject: {}",
                        msg.getId(), msg.getSubject() );
                continue;
            }
            dao.markAirbnbPaymentNeedsReviewFromCancelEmail( code, msg.getSubject() );
            LOGGER.info( "Marked {} needs_review from cancel email {}", code, msg.getId() );
        }
        return processed;
    }

    private boolean shouldSkipEnqueue( AirbnbPayment payment, EmailTotals totals ) {
        String status = payment.getProcessingStatus();
        if ( AirbnbPayment.STATUS_IGNORED.equals( status ) ) {
            return true;
        }
        if ( AirbnbPayment.STATUS_NEEDS_REVIEW.equals( status )
                && StringUtils.startsWith( payment.getStatusReason(), "Airbnb cancel email:" ) ) {
            // Cancel notice takes precedence until CSV/manual clears it — still allow resolution
            // remittance jobs when email totals include money movement.
            if ( false == totals.hasReservation() && false == totals.hasResolution() ) {
                return true;
            }
        }
        if ( AirbnbPayment.STATUS_PROCESSING.equals( status )
                && dao.hasPendingAirbnbImportTransactionJob( payment.getConfirmationCode() ) ) {
            return true;
        }
        if ( false == AirbnbPayment.STATUS_PROCESSED.equals( status ) ) {
            return false;
        }

        BigDecimal posted = nullToZero( payment.getPostAmount() );
        BigDecimal emailNet = totals.getNetAmount();
        if ( withinTolerance( posted, emailNet ) ) {
            LOGGER.info( "Skipping enqueue for {}: already processed (posted {}, email net {})",
                    payment.getConfirmationCode(), posted, emailNet );
            return true;
        }
        // New resolution (or other remittance change) after a successful post — re-reconcile.
        if ( totals.hasResolution() || totals.hasReservation() ) {
            return false;
        }

        payment.setProcessingStatus( AirbnbPayment.STATUS_NEEDS_REVIEW );
        payment.setStatusReason( StringUtils.abbreviate(
                "Email net " + emailNet + " differs from processed " + posted, 500 ) );
        dao.updateAirbnbPayment( payment );
        LOGGER.warn( "Confirmation {}: email net {} vs processed {}; marked needs_review",
                payment.getConfirmationCode(), emailNet, posted );
        return true;
    }

    private static boolean withinTolerance( BigDecimal a, BigDecimal b ) {
        return nullToZero( a ).subtract( nullToZero( b ) ).abs().compareTo( TOLERANCE ) <= 0;
    }

    private static BigDecimal nullToZero( BigDecimal v ) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public int getDaysBack() {
        String daysBack = getParameter( "days_back" );
        return daysBack == null ? 14 : Integer.parseInt( daysBack );
    }

    public void setDaysBack( int daysBack ) {
        if ( daysBack < 1 ) {
            throw new IllegalArgumentException( "days_back must be >= 1" );
        }
        setParameter( "days_back", String.valueOf( daysBack ) );
    }
}
