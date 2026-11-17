package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.AirbnbPayment;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Reconciles an imported Airbnb confirmation-code row against the live Cloudbeds folio.
 */
@Service
public class AirbnbPaymentReconcileService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    static final BigDecimal TOLERANCE = new BigDecimal( "0.10" );

    public static final String ACTION_AUTO = "auto";
    public static final String ACTION_POST_AMOUNT = "post_amount";
    public static final String ACTION_POST_GROSS = "post_gross";
    public static final String ACTION_VOID_REPOST = "void_repost";
    public static final String ACTION_MARK_PROCESSED = "mark_processed";
    public static final String ACTION_MARK_IGNORED = "mark_ignored";

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private WordPressDAO dao;

    public static class ReconcileResult {
        private final String status;
        private final String reason;

        public ReconcileResult( String status, String reason ) {
            this.status = status;
            this.reason = reason;
        }

        public String getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }
    }

    public ReconcileResult reconcile( WebClient webClient, AirbnbPayment payment, String action )
            throws Exception {
        if ( ACTION_MARK_PROCESSED.equals( action ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED, "Marked processed manually" );
        }
        if ( ACTION_MARK_IGNORED.equals( action ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_IGNORED, "Marked ignored manually" );
        }

        Reservation res = matchReservation( webClient, payment );
        payment.setReservationId( Long.valueOf( res.getReservationId() ) );
        payment.setMatchedYn( "Y" );
        dao.updateAirbnbPayment( payment );

        if ( ACTION_POST_AMOUNT.equals( action ) || ACTION_POST_GROSS.equals( action )
                || ACTION_VOID_REPOST.equals( action ) ) {
            return reconcileReservationOnly( webClient, payment, res, action );
        }

        if ( payment.isYn( payment.getHasCancellationFee() )
                && false == payment.isYn( payment.getHasReservation() ) ) {
            return reconcileCancellationFeeOnly( webClient, payment, res );
        }
        if ( payment.isYn( payment.getHasResolutionAdjustment() ) ) {
            return reconcileResolution( webClient, payment, res );
        }
        if ( payment.isYn( payment.getHasReservation() )
                && false == payment.isYn( payment.getHasResolutionAdjustment() )
                && false == payment.isYn( payment.getHasCancellationFee() )
                && false == payment.isYn( payment.getHasAdjustment() ) ) {
            return reconcileReservationOnly( webClient, payment, res, ACTION_AUTO );
        }
        return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                "Unsupported line mix: " + payment.getLineTypes() );
    }

    private Reservation matchReservation( WebClient webClient, AirbnbPayment payment ) throws IOException {
        List<Customer> matches = cloudbedsScraper.getReservations( webClient, payment.getConfirmationCode() );
        List<Customer> exact = matches.stream()
                .filter( c -> payment.getConfirmationCode()
                        .equalsIgnoreCase( StringUtils.trimToEmpty( c.getThirdPartyIdentifier() ) ) )
                .collect( Collectors.toList() );
        if ( exact.isEmpty() ) {
            throw new MissingUserDataException(
                    "No Cloudbeds booking with third-party id " + payment.getConfirmationCode() );
        }
        if ( exact.size() > 1 && payment.getStartDate() != null && payment.getEndDate() != null ) {
            String start = payment.getStartDate().toLocalDate().toString();
            String end = payment.getEndDate().toLocalDate().toString();
            exact = exact.stream()
                    .filter( c -> start.equals( c.getCheckinDate() ) && end.equals( c.getCheckoutDate() ) )
                    .collect( Collectors.toList() );
        }
        if ( exact.size() != 1 ) {
            throw new UnrecoverableFault( "Ambiguous Cloudbeds match for " + payment.getConfirmationCode() );
        }
        Reservation res = cloudbedsScraper.getReservationRetry( webClient, exact.get( 0 ).getId() );
        if ( false == res.isAirbnbBooking() || false == res.isChannelCollectBooking() ) {
            throw new UnrecoverableFault( "Reservation " + res.getReservationId()
                    + " is not Airbnb channel-collect" );
        }
        if ( payment.getStartDate() != null && payment.getEndDate() != null ) {
            LocalDate start = payment.getStartDate().toLocalDate();
            LocalDate end = payment.getEndDate().toLocalDate();
            if ( false == start.equals( res.getCheckinDateAsLocalDate() )
                    || false == end.equals( res.getCheckoutDateAsLocalDate() ) ) {
                throw new UnrecoverableFault( "Start/end dates do not match Cloudbeds for "
                        + payment.getConfirmationCode() + " (CB "
                        + res.getCheckinDate() + "–" + res.getCheckoutDate() + ")" );
            }
        }
        return res;
    }

    private ReconcileResult reconcileReservationOnly( WebClient webClient, AirbnbPayment payment,
            Reservation res, String action ) throws Exception {
        boolean useGross = AirbnbPayment.FIELD_GROSS.equalsIgnoreCase( payment.getPostPaymentField() )
                || ACTION_POST_GROSS.equals( action );
        if ( ACTION_POST_AMOUNT.equals( action ) ) {
            useGross = false;
        }
        BigDecimal targetPost = useGross
                ? nullToZero( payment.getGrossEarnings() != null ? payment.getGrossEarnings() : payment.getNetGrossEarnings() )
                : nullToZero( payment.getAmount() != null ? payment.getAmount() : payment.getNetAmount() );
        BigDecimal serviceFee = nullToZero( payment.getServiceFee() );
        BigDecimal gross = nullToZero( payment.getGrossEarnings() );

        if ( false == useGross && gross.compareTo( BigDecimal.ZERO ) > 0 ) {
            BigDecimal roomTotal = roomRatePlusVat( webClient, res );
            if ( false == withinTolerance( roomTotal, gross ) ) {
                return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                        "Room rate+VAT " + roomTotal + " does not match Gross earnings " + gross );
            }
            if ( serviceFee.compareTo( BigDecimal.ZERO ) > 0
                    && false == hasServiceFeeAdjust( webClient, res ) ) {
                cloudbedsScraper.addManualRoomRevenueAdjust( webClient, res, serviceFee.negate(),
                        CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE );
            }
        }

        List<TransactionRecord> txns = cloudbedsScraper.getTransactionsByReservation( webClient,
                res.getReservationId() );
        List<TransactionRecord> airbnbPayments = airbnbPaymentLines( txns );
        List<TransactionRecord> unsafeCredits = unsafeNonAirbnbPaymentCredits( txns );
        if ( false == unsafeCredits.isEmpty() && false == ACTION_VOID_REPOST.equals( action ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                    "Unexpected non-Airbnb payment credits on folio" );
        }

        BigDecimal netPrepaid = netAirbnbPrepaid( airbnbPayments );
        if ( withinTolerance( netPrepaid, targetPost ) && unsafeCredits.isEmpty() ) {
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                    "Posted amount already present. nothing to do." );
        }

        // Void mismatched airbnb/ronbot payment lines (positive paid only)
        for ( TransactionRecord txn : airbnbPayments ) {
            BigDecimal paid = txn.getPaidAsBigDecimal();
            if ( paid != null && paid.compareTo( BigDecimal.ZERO ) > 0 && txn.isVoidable()
                    && false == txn.isVoided()
                    && StringUtils.isNotBlank( txn.getPaymentId() ) ) {
                cloudbedsScraper.voidFolioPayment( webClient, res.getReservationId(), txn.getPaymentId() );
            }
        }

        // Re-read after voids
        txns = cloudbedsScraper.getTransactionsByReservation( webClient, res.getReservationId() );
        netPrepaid = netAirbnbPrepaid( airbnbPaymentLines( txns ) );
        if ( withinTolerance( netPrepaid, targetPost ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                    "Corrected folio to target post amount " + targetPost );
        }
        if ( netPrepaid.compareTo( BigDecimal.ZERO ) == 0 && targetPost.compareTo( BigDecimal.ZERO ) > 0 ) {
            cloudbedsScraper.addAirbnbPrepaidPayment( webClient, res, targetPost,
                    CloudbedsScraper.AIRBNB_PREPAID_PAYMENT_DESCRIPTION );
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                    "Posted Airbnb prepaid " + targetPost );
        }
        return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                "After void, net Airbnb prepaid " + netPrepaid + " != target " + targetPost );
    }

    private ReconcileResult reconcileResolution( WebClient webClient, AirbnbPayment payment, Reservation res )
            throws Exception {
        BigDecimal clawback = nullToZero( payment.getNetAmount() ).abs();
        // Prefer reservation amount clawback magnitude from negative resolution lines:
        // net_amount already includes reservation + resolution.
        BigDecimal reservationAmount = nullToZero( payment.getAmount() );
        BigDecimal resolutionClawback = reservationAmount.subtract( nullToZero( payment.getNetAmount() ) ).abs();
        if ( payment.isYn( payment.getHasReservation() ) && resolutionClawback.compareTo( BigDecimal.ZERO ) > 0 ) {
            clawback = resolutionClawback;
        }

        List<TransactionRecord> airbnbPayments = airbnbPaymentLines(
                cloudbedsScraper.getTransactionsByReservation( webClient, res.getReservationId() ) );
        BigDecimal netPrepaid = netAirbnbPrepaid( airbnbPayments );

        boolean fullClawback = withinTolerance( nullToZero( payment.getNetAmount() ), BigDecimal.ZERO );
        if ( fullClawback ) {
            if ( withinTolerance( netPrepaid, BigDecimal.ZERO ) ) {
                return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                        "Full clawback already settled (net prepaid 0)" );
            }
            if ( withinTolerance( netPrepaid, clawback ) ) {
                cloudbedsScraper.addAirbnbPrepaidRefund( webClient, res, clawback,
                        CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION );
                return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                        "Refunded full clawback " + clawback );
            }
            return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                    "Full clawback but net prepaid " + netPrepaid + " != clawback " + clawback );
        }

        // Partial
        if ( hasResolutionRefund( airbnbPayments, clawback ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                    "Resolution refund already present" );
        }
        if ( netPrepaid.compareTo( clawback.subtract( TOLERANCE ) ) >= 0 ) {
            cloudbedsScraper.addAirbnbPrepaidRefund( webClient, res, clawback,
                    CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION );
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                    "Posted resolution refund " + clawback );
        }
        return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                "Partial clawback " + clawback + " exceeds net prepaid " + netPrepaid );
    }

    private ReconcileResult reconcileCancellationFeeOnly( WebClient webClient, AirbnbPayment payment,
            Reservation res ) throws Exception {
        BigDecimal cancellationAmount = nullToZero( payment.getNetAmount() );
        if ( cancellationAmount.compareTo( BigDecimal.ZERO ) > 0 ) {
            return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                    "Positive Cancellation Fee not auto-handled" );
        }
        List<TransactionRecord> airbnbPayments = airbnbPaymentLines(
                cloudbedsScraper.getTransactionsByReservation( webClient, res.getReservationId() ) );
        BigDecimal netPrepaid = netAirbnbPrepaid( airbnbPayments );
        if ( withinTolerance( netPrepaid, BigDecimal.ZERO ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                    "Cancellation Fee with no prepaid; nothing to do" );
        }
        if ( false == "canceled".equalsIgnoreCase( res.getStatus() ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                    "Cancellation Fee but booking not canceled; net prepaid " + netPrepaid );
        }
        for ( TransactionRecord txn : airbnbPayments ) {
            BigDecimal paid = txn.getPaidAsBigDecimal();
            if ( paid != null && paid.compareTo( BigDecimal.ZERO ) > 0 && txn.isVoidable()
                    && false == txn.isVoided() && StringUtils.isNotBlank( txn.getPaymentId() ) ) {
                cloudbedsScraper.voidFolioPayment( webClient, res.getReservationId(), txn.getPaymentId() );
            }
        }
        Reservation refreshed = cloudbedsScraper.getReservationRetry( webClient, res.getReservationId() );
        BigDecimal balanceDue = refreshed.getBalanceDue() != null ? refreshed.getBalanceDue() : BigDecimal.ZERO;
        if ( withinTolerance( balanceDue, BigDecimal.ZERO ) ) {
            return new ReconcileResult( AirbnbPayment.STATUS_PROCESSED,
                    "Voided stray prepaid on canceled booking" );
        }
        return new ReconcileResult( AirbnbPayment.STATUS_NEEDS_REVIEW,
                "After voiding prepaid, balanceDue=" + balanceDue );
    }

    private BigDecimal roomRatePlusVat( WebClient webClient, Reservation res ) throws IOException {
        List<TransactionRecord> txns = cloudbedsScraper.getTransactionsByReservation( webClient,
                res.getReservationId() );
        BigDecimal total = BigDecimal.ZERO;
        for ( TransactionRecord txn : txns ) {
            if ( txn.isVoided() ) {
                continue;
            }
            String type = StringUtils.defaultString( txn.getType() );
            String desc = StringUtils.defaultString( txn.getDescription() );
            if ( "rate".equalsIgnoreCase( type )
                    || ( "tax".equalsIgnoreCase( type ) && "VAT".equalsIgnoreCase( desc ) ) ) {
                BigDecimal credit = txn.getCreditAsBigDecimal();
                if ( credit != null ) {
                    total = total.add( credit );
                }
            }
        }
        return total.setScale( 2, RoundingMode.HALF_UP );
    }

    private boolean hasServiceFeeAdjust( WebClient webClient, Reservation res ) throws IOException {
        return cloudbedsScraper.getTransactionsByReservation( webClient, res.getReservationId() ).stream()
                .filter( t -> false == t.isVoided() )
                .anyMatch( t -> StringUtils.containsIgnoreCase( t.getNotes(),
                        CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE )
                        || StringUtils.containsIgnoreCase( t.getDescription(),
                                CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE ) );
    }

    static List<TransactionRecord> airbnbPaymentLines( List<TransactionRecord> txns ) {
        List<TransactionRecord> out = new ArrayList<>();
        for ( TransactionRecord t : txns ) {
            if ( false == "payment".equalsIgnoreCase( t.getType() ) || t.isVoided() ) {
                continue;
            }
            if ( isAirbnbRelatedPayment( t ) ) {
                out.add( t );
            }
        }
        return out;
    }

    static boolean isAirbnbRelatedPayment( TransactionRecord t ) {
        String paymentType = StringUtils.defaultString( t.getPaymentType() );
        String notes = StringUtils.defaultString( t.getNotes() );
        String desc = StringUtils.defaultString( t.getDescription() );
        return "airbnb".equalsIgnoreCase( paymentType )
                || StringUtils.containsIgnoreCase( desc, "AirBnB Prepaid" )
                || StringUtils.containsIgnoreCase( notes, CloudbedsScraper.AIRBNB_PREPAID_PAYMENT_DESCRIPTION )
                || StringUtils.containsIgnoreCase( notes, CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION )
                || StringUtils.containsIgnoreCase( desc, CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION );
    }

    static List<TransactionRecord> unsafeNonAirbnbPaymentCredits( List<TransactionRecord> txns ) {
        List<TransactionRecord> out = new ArrayList<>();
        for ( TransactionRecord t : txns ) {
            if ( false == "payment".equalsIgnoreCase( t.getType() ) || t.isVoided() ) {
                continue;
            }
            if ( isAirbnbRelatedPayment( t ) ) {
                continue;
            }
            String paymentType = StringUtils.defaultString( t.getPaymentType() );
            if ( "OnsiteTerminal".equalsIgnoreCase( paymentType )
                    || StringUtils.containsIgnoreCase( t.getDescription(), "Onsite Terminal" ) ) {
                continue;
            }
            BigDecimal paid = t.getPaidAsBigDecimal();
            if ( paid != null && paid.compareTo( BigDecimal.ZERO ) > 0 ) {
                out.add( t );
            }
        }
        return out;
    }

    static BigDecimal netAirbnbPrepaid( List<TransactionRecord> airbnbPayments ) {
        BigDecimal net = BigDecimal.ZERO;
        for ( TransactionRecord t : airbnbPayments ) {
            BigDecimal paid = t.getPaidAsBigDecimal();
            if ( paid != null ) {
                net = net.add( paid );
            }
        }
        return net.setScale( 2, RoundingMode.HALF_UP );
    }

    static boolean hasResolutionRefund( List<TransactionRecord> airbnbPayments, BigDecimal clawback ) {
        for ( TransactionRecord t : airbnbPayments ) {
            String notes = StringUtils.defaultString( t.getNotes() );
            String desc = StringUtils.defaultString( t.getDescription() );
            BigDecimal paid = t.getPaidAsBigDecimal();
            if ( paid != null && paid.compareTo( BigDecimal.ZERO ) < 0
                    && withinTolerance( paid.abs(), clawback )
                    && ( StringUtils.containsIgnoreCase( notes,
                            CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION )
                            || StringUtils.containsIgnoreCase( desc,
                                    CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION ) ) ) {
                return true;
            }
        }
        return false;
    }

    static boolean withinTolerance( BigDecimal a, BigDecimal b ) {
        return a.subtract( b ).abs().compareTo( TOLERANCE ) <= 0;
    }

    static BigDecimal nullToZero( BigDecimal value ) {
        return value == null ? BigDecimal.ZERO.setScale( 2, RoundingMode.HALF_UP )
                : value.setScale( 2, RoundingMode.HALF_UP );
    }
}
