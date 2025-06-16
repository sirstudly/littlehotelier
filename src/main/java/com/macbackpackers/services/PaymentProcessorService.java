
package com.macbackpackers.services;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.StripeRefund;
import com.macbackpackers.beans.StripeTransaction;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.PaymentPendingException;
import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.jobs.ArchiveAllTransactionNotesJob;
import com.macbackpackers.jobs.BDCMarkCreditCardInvalidJob;
import com.macbackpackers.jobs.SendDepositChargeDeclinedEmailJob;
import com.macbackpackers.jobs.SendDepositChargeSuccessfulEmailJob;
import com.macbackpackers.jobs.SendHostelworldLateCancellationEmailJob;
import com.macbackpackers.jobs.SendNonRefundableDeclinedEmailJob;
import com.macbackpackers.jobs.SendNonRefundableSuccessfulEmailJob;
import com.macbackpackers.jobs.SendRefundSuccessfulEmailJob;
import com.macbackpackers.jobs.SendStripePaymentConfirmationEmailJob;
import com.macbackpackers.jobs.SendTemplatedEmailJob;
import com.macbackpackers.scrapers.BookingComScraper;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.HostelworldScraper;
import com.stripe.Stripe;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Charge.Outcome;
import com.stripe.model.Charge.PaymentMethodDetails;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;
import com.stripe.param.RefundCreateParams;
import org.htmlunit.WebClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.macbackpackers.scrapers.CloudbedsScraper.TEMPLATE_PAYMENT_DECLINED;

/**
 * Service for checking payments in LH, sending (deposit) payments through the payment gateway and
 * recording said payments in LH.
 */
@Service
public class PaymentProcessorService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    public static final String CHARGE_REMAINING_BALANCE_NOTE = "Attempting to charge remaining balance for booking.";
    public static final BigDecimal MINIMUM_CHARGE_AMOUNT = new BigDecimal( "0.3" );

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AutowireCapableBeanFactory autowireBeanFactory;

    @Autowired
    private HostelworldScraper hostelworldScraper;

    @Autowired
    private WordPressDAO wordpressDAO;

    @Autowired
    private ExpediaApiService expediaService;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private CloudbedsService cloudbedsService;

    @Autowired
    private BookingComScraper bdcScraper;

    /**
     * Attempt to charge the deposit payment for the given reservation. This method does nothing if
     * the payment outstanding != payment total.
     *
     * @param webClient web client to use
     * @param reservationId unique CB reservation
     * @throws IOException on I/O error
     */
    public synchronized void processDepositPayment( WebClient webClient, String reservationId ) throws IOException {
        LOGGER.info( "Processing deposit payment for reservation " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        // check if we have anything to pay
        if ( cbReservation.getPaidValue().compareTo( BigDecimal.ZERO ) > 0 ) {
            LOGGER.info( "Booking has non-zero paid amount. Not charging deposit." );
            return;
        }
        else if ( "canceled".equals( cbReservation.getStatus() ) ) {
            LOGGER.info( "Booking is cancelled. Not charging." );
            return;
        }
        // group bookings must be approved/charged manually
        else if ( cbReservation.getNumberOfGuests() >= wordpressDAO.getGroupBookingSize() ) {
            LOGGER.info( "Reservation " + reservationId + " has " + cbReservation.getNumberOfGuests() + " guests. Payment must be done manually for groups." );
            return;
        }

        // check if card details exist in CB
        if ( false == cbReservation.isCardDetailsPresent() ) {
            throw new MissingUserDataException( "Missing card details found for reservation " + cbReservation.getReservationId() + ". Unable to continue." );
        }

        // either take first night, or a percentage amount
        BigDecimal depositAmount;
        final String depositStrategy = wordpressDAO.getMandatoryOption( "hbo_bdc_deposit_strategy" );
        if ( false == "first_night".equals( depositStrategy ) && "Booking.com".equals( cbReservation.getSourceName() ) ) {
            BigDecimal percentToCharge = new BigDecimal( depositStrategy );
            depositAmount = cbReservation.getGrandTotal().multiply( percentToCharge ).setScale( 2, RoundingMode.HALF_UP );
            LOGGER.info( "Deposit amount due: " + depositAmount );
        }
        else {
            depositAmount = cbReservation.getRateFirstNight( gson );
            LOGGER.info( "First night due: " + depositAmount );
        }

        if ( depositAmount.compareTo( BigDecimal.ZERO ) <= 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount must be greater than 0." );
        }
        if ( depositAmount.compareTo( cbReservation.getBalanceDue() ) > 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount exceeds balance due." );
        }

        try {
            // should have credit card details at this point; attempt autocharge
            cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, depositAmount );
            cloudbedsScraper.addArchivedNote( webClient, reservationId,
                    "Successfully charged deposit of £" + cloudbedsScraper.getCurrencyFormat().format( depositAmount ) );

            // send email if successful
            SendDepositChargeSuccessfulEmailJob job = new SendDepositChargeSuccessfulEmailJob();
            job.setReservationId( reservationId );
            job.setAmount( depositAmount );
            job.setStatus( JobStatus.submitted );
            wordpressDAO.insertJob( job );
        }
        catch ( PaymentPendingException ex ) {
            LOGGER.info( ex.getMessage() );
            cloudbedsScraper.addNote( webClient, reservationId,
                    "Pending deposit of £" + cloudbedsScraper.getCurrencyFormat().format( depositAmount )
                            + " will post automatically when customer confirms via email." );
        }
        catch ( RecordPaymentFailedException payEx ) {
            LOGGER.info( "Unable to process payment: " + payEx.getMessage() );

            if ( cloudbedsScraper.getEmailLastSentDate( webClient, reservationId,
                    CloudbedsScraper.TEMPLATE_DEPOSIT_CHARGE_DECLINED ).isPresent() ) {
                LOGGER.info( "Declined payment email already sent. Not going to do it again..." );
            }
            else {
                LOGGER.info( "Creating declined deposit charge email job" );
                String paymentURL = cloudbedsService.generateUniquePaymentURL( reservationId, depositAmount );
                SendDepositChargeDeclinedEmailJob job = new SendDepositChargeDeclinedEmailJob();
                job.setReservationId( reservationId );
                job.setAmount( depositAmount );
                job.setPaymentURL( paymentURL );
                job.setStatus( JobStatus.submitted );
                wordpressDAO.insertJob( job );

                if ( "Booking.com".equals( cbReservation.getSourceName() ) &&
                        false == cbReservation.containsNote( "Marking card ending in " + cbReservation.getCreditCardLast4Digits() + " as invalid for reservation" ) &&
                        wordpressDAO.getOption( "hbo_bdc_username" ) != null ) {
                    BDCMarkCreditCardInvalidJob j = new BDCMarkCreditCardInvalidJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( reservationId );
                    wordpressDAO.insertJob( j );
                }
            }
        }
    }

    /**
     * Copies the card details (for HWL/BDC/EXP) to CB if it doesn't already exist.
     *
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @return the reloaded reservation
     * @throws Exception on failure
     */
    public Reservation copyCardDetailsToCloudbeds( WebClient webClient, String reservationId ) throws Exception {
        return copyCardDetailsToCloudbeds( webClient, cloudbedsScraper.getReservationRetry( webClient, reservationId ) );
    }

    /**
     * Copies the card details (for HWL/BDC/EXP) to CB if it doesn't already exist.
     *
     * @param webClient web client for cloudbeds
     * @param cbReservation the loaded cloudbeds reservation
     * @return the reloaded reservation
     * @throws Exception on failure
     */
    public Reservation copyCardDetailsToCloudbeds( WebClient webClient, Reservation cbReservation ) throws Exception {
        LOGGER.info( "Copying card details for reservation " + cbReservation.getReservationId() );

        // check if card details exist in CB
        if ( cbReservation.isCardDetailsPresent() ) {
            LOGGER.info( "Card details already exist; copying anyways..." );
        }

        CardDetails ccDetails = null;
        if ( cbReservation.getSourceName().startsWith( "Hostelworld" ) ) {
            String hwlRef = "HWL-" + cbReservation.getThirdPartyIdentifier();
            LOGGER.info( "Retrieving HWL customer card details for " + hwlRef );
            ccDetails = retrieveHWCardDetails( hwlRef );
        }
        else if ( cbReservation.getSourceName().startsWith( "Expedia" ) ) {
            LOGGER.info( "Retrieving EXP customer card details" );
            ccDetails = expediaService.returnCardDetailsForBooking(
                    cbReservation.getThirdPartyIdentifier() ).getCardDetails();
        }
        else if ( "Booking.com".equals( cbReservation.getSourceName() ) ) {
            LOGGER.info( "Retrieving BDC customer card details for BDC#" + cbReservation.getThirdPartyIdentifier() );
            try (WebClient webClientForBDC = context.getBean( "webClientForBDC", WebClient.class )) {
                ccDetails = bdcScraper.returnCardDetailsForBooking( webClientForBDC, cbReservation.getThirdPartyIdentifier() );
            }

            // use Selenium driver
//            try (AutocloseableWebDriver driver = new AutocloseableWebDriver()) {
//                ccDetails = bdcSeleniumScraper.returnCardDetailsForBooking(driver.getDriver(), driver.getDriverWait(), cbReservation.getThirdPartyIdentifier());
//            }
        }
        //        else if ( bookingRef.startsWith( "AGO-" ) && isCardDetailsBlank ) {
        //            LOGGER.info( "Retrieving AGO customer card details" );
        //            // I don't think this actually works anymore but give it a go anyways
        //            ccDetails = retrieveAgodaCardDetails( reservationPage, bookingRef );
        //        }
        else {
            throw new UnsupportedOperationException( "Unsupported source " + cbReservation.getSourceName() );
        }
        LOGGER.info( "Retrieved card: " + new BasicCardMask().applyCardMask( ccDetails.getCardNumber() )
                + " for " + ccDetails.getName() );

        // if we're missing the cardholder name; just use the guest name
        if ( StringUtils.isBlank( ccDetails.getName() ) ) {
            ccDetails.setName( cbReservation.getFirstName() + cbReservation.getLastName() );
        }
        cloudbedsScraper.addCardDetails( webClient, cbReservation.getReservationId(), ccDetails );
        return cloudbedsScraper.getReservationRetry( webClient, cbReservation.getReservationId() ); // reload reservation
    }

    /**
     * Does a AUTHORIZE/CAPTURE on the card details on the booking for the balance remaining if the
     * Rate Plan is "Non-refundable".
     * 
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @throws Exception on any kind of error
     */
    public void chargeNonRefundableBooking( WebClient webClient, String reservationId ) throws Exception {
        LOGGER.info( "Processing charge of non-refundable booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        // check if we have anything to pay
        if ( cbReservation.isPaid() ) {
            LOGGER.info( "Booking is paid. Nothing to do." );
            return;
        }

        if ( false == cbReservation.isNonRefundable() ) {
            throw new UnrecoverableFault( "ABORT! Attempting to charge a non non-refundable booking!" );
        }
        // group bookings must be approved/charged manually
        else if ( cbReservation.getNumberOfGuests() >= wordpressDAO.getGroupBookingSize() ) {
            LOGGER.info( "Reservation " + reservationId + " has " + cbReservation.getNumberOfGuests() + " guests. Payment must be done manually for groups." );
            return;
        }

        // check if card details exist in CB; copy over if req'd
        if ( false == cbReservation.isCardDetailsPresent() ) {
            final String NOTE = "Missing card details found for reservation " + cbReservation.getReservationId();

            if ( false == cbReservation.containsNote( NOTE ) ) {
                cloudbedsScraper.addNote( webClient, reservationId, NOTE );
            }

            cbReservation = copyCardDetailsToCloudbeds( webClient, cbReservation );

            // if still not available, something has gone wrong somewhere
            if ( false == cbReservation.isCardDetailsPresent() ) {
                throw new MissingUserDataException( "Missing card details found for reservation " + cbReservation.getReservationId() + ". Unable to continue." );
            }
        }

        final String WILL_POST_AUTOMATICALLY = " will post automatically when customer confirms via email.";
        try {
            if ( cbReservation.containsNote( WILL_POST_AUTOMATICALLY ) ) {
                LOGGER.info( "We've already been thru this before; yet there is still a balance owing..." );
                throw new RecordPaymentFailedException( "Payment previously attempted but was never charged successfully..." );
            }

            // should have credit card details at this point; attempt autocharge
            cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, cbReservation.getBalanceDue() );
            cloudbedsScraper.addArchivedNote( webClient, reservationId, "Outstanding balance of "
                    + cloudbedsScraper.getCurrencyFormat().format( cbReservation.getBalanceDue() ) + " successfully charged." );

            // mark booking as fully paid in Hostelworld
//            if ( Arrays.asList( "Hostelworld & Hostelbookers", "Hostelworld" ).contains( cbReservation.getSourceName() ) ) {
//                HostelworldAcknowledgeFullPaymentTakenJob ackJob = new HostelworldAcknowledgeFullPaymentTakenJob();
//                ackJob.setHostelworldBookingRef( cbReservation.getThirdPartyIdentifier() );
//                ackJob.setStatus( JobStatus.submitted );
//                wordpressDAO.insertJob( ackJob );
//            }

            // send email if successful
            SendNonRefundableSuccessfulEmailJob job = new SendNonRefundableSuccessfulEmailJob();
            job.setReservationId( reservationId );
            job.setAmount( cbReservation.getBalanceDue() );
            job.setStatus( JobStatus.submitted );
            wordpressDAO.insertJob( job );
        }
        catch ( PaymentPendingException ex ) {
            LOGGER.info( ex.getMessage() );
            cloudbedsScraper.addNote( webClient, reservationId,
                    "Non-refundable amount of £" + cloudbedsScraper.getCurrencyFormat().format( cbReservation.getBalanceDue() )
                            + WILL_POST_AUTOMATICALLY );
        }
        catch ( RecordPaymentFailedException payEx ) {
            LOGGER.info( "Unable to process payment: " + payEx.getMessage() );

            if ( cloudbedsScraper.getEmailLastSentDate( webClient, reservationId,
                    CloudbedsScraper.TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED ).isPresent() ||
                    cbReservation.containsNote( CloudbedsScraper.TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED ) ) {
                LOGGER.info( "Declined payment email already sent. Not going to do it again..." );
            }
            else {
                LOGGER.info( "Sending declined payment email" );
                String paymentURL = cloudbedsService.generateUniquePaymentURL( reservationId, null );

                SendNonRefundableDeclinedEmailJob job = new SendNonRefundableDeclinedEmailJob();
                job.setReservationId( reservationId );
                job.setAmount( cbReservation.getBalanceDue() );
                job.setPaymentURL( paymentURL );
                job.setStatus( JobStatus.submitted );
                wordpressDAO.insertJob( job );
            }
        }
    }

    /**
     * Does a AUTHORIZE/CAPTURE on the card details on the prepaid BDC booking.
     * 
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @throws Exception
     */
    public synchronized void processPrepaidBooking( WebClient webClient, String reservationId ) throws Exception {
        LOGGER.info( "Processing full payment for booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        // check if we have anything to pay
        if ( cbReservation.isPaid() ) {
            LOGGER.warn( "Booking is paid! Stopping here." );
            return;
        }

        if ( false == cbReservation.isCardDetailsPresent() ) {
            cbReservation = copyCardDetailsToCloudbeds( webClient, cbReservation );
        }
        if ( false == "Booking.com".equals( cbReservation.getSourceName() ) ) {
            throw new MissingUserDataException( "Unsupported source " + cbReservation.getSourceName() );
        }
        if ( false == cbReservation.isPrepaid() ) {
            throw new MissingUserDataException( "Booking not identified as a prepaid booking" );
        }
        if ( false == cbReservation.isChannelCollectBooking() ) {
            throw new MissingUserDataException( "Booking not identified as a channel-collect booking" );
        }

        // should have credit card details at this point; attempt AUTHORIZE/CAPTURE
        // if we have BDC login details, try to get the VCC balance to charge
        if ( wordpressDAO.getOption( "hbo_bdc_username" ) != null ) {
            if ( cbReservation.containsNote( "VCC has been charged for the full amount" ) ) {
                LOGGER.info( "I think this has already been charged... Nothing to do." );
                return;
            }
            LOGGER.info( "Looks like a prepaid card... Looking up actual value to charge on BDC" );
            final BigDecimal amountToCharge;
            try (WebClient webClientForBDC = context.getBean( "webClientForBDC", WebClient.class )) {
                amountToCharge = bdcScraper.getVirtualCardBalance( webClientForBDC, cbReservation.getThirdPartyIdentifier() );
            }
            final String MODIFIED_OUTSIDE_OF_BDC = "IMPORTANT: The PREPAID booking seems to have been modified outside of BDC (or payment was incorrectly collected from guest). "
                    + "VCC has been charged for the full amount so any outstanding balance should be PAID BY THE GUEST ON ARRIVAL.";
            LOGGER.info( "VCC balance is " + cloudbedsScraper.getCurrencyFormat().format( amountToCharge ) + "." );
            if ( amountToCharge.compareTo( MINIMUM_CHARGE_AMOUNT ) > 0 ) {
                cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, amountToCharge );

                if ( 0 != cbReservation.getBalanceDue().compareTo( amountToCharge )
                        && false == "canceled".equals( cbReservation.getStatus() ) ) {
                    cloudbedsScraper.addNote( webClient, reservationId, MODIFIED_OUTSIDE_OF_BDC );
                }
            }
            else {
                final String MINIMUM_CHARGE_NOTE = "Minimum charge amount not reached. Remaining balance not charged.";
                if ( cbReservation.getBalanceDue().compareTo( MINIMUM_CHARGE_AMOUNT ) > 0
                        && false == "canceled".equals( cbReservation.getStatus() ) ) {
                    cloudbedsScraper.addNote( webClient, reservationId, MODIFIED_OUTSIDE_OF_BDC );
                }
                else if ( cbReservation.containsNote( MINIMUM_CHARGE_NOTE ) ) {
                    LOGGER.info( MINIMUM_CHARGE_NOTE );
                }
                else {
                    cloudbedsScraper.addNote( webClient, reservationId, MINIMUM_CHARGE_NOTE );
                }
            }
        }
        else {
            try {
                cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, cbReservation.getBalanceDue() );
            }
            catch( RecordPaymentFailedException rpfe ) {
                if ( rpfe.getMessage().contains( "insufficient funds" ) && cbReservation.isPossibleHotelCollectSmartFlexReservation() ) {
                    final String SMART_FLEX_NOTE = "THIS DOES NOT APPEAR TO BE A CHANNEL-COLLECT BOOKING! GUEST TO PAY BALANCE ON ARRIVAL.";
                    if ( cbReservation.containsNote( SMART_FLEX_NOTE ) ) {
                        LOGGER.info( SMART_FLEX_NOTE );
                    }
                    else {
                        cloudbedsScraper.addNote( webClient, reservationId, SMART_FLEX_NOTE );
                    }
                }
            }
        }
    }

    /**
     * Does a REFUND on the card details for the prepaid BDC booking.
     *
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @param amountToRefund amount being refunded
     * @param description description to go onto the refund payment
     * @throws Exception
     */
    public synchronized void processPrepaidRefund(WebClient webClient, String reservationId, BigDecimal amountToRefund, String description) throws Exception {
        LOGGER.info( "Processing refund for booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry(webClient, reservationId);
        LOGGER.info("Amount being refunded: " + amountToRefund);
        LOGGER.info("Refund description: " + description);

        if (false == "Booking.com".equals(cbReservation.getSourceName())) {
            throw new MissingUserDataException("Unsupported source " + cbReservation.getSourceName());
        }
        if (false == cbReservation.isPrepaid()) {
            throw new MissingUserDataException("Booking not identified as a prepaid booking");
        }
        if (amountToRefund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MissingUserDataException("Refund amount must be greater than 0");
        }

        List<TransactionRecord> records = cloudbedsScraper.getTransactionsForRefund(webClient, reservationId);
        if (records.stream()
                .map(txn -> txn.getCardNumber())
                .distinct()
                .collect(Collectors.toList())
                .size() > 1) {
            throw new UnrecoverableFault("Multiple cards are available to refund against. Please refund manually.");
        }

        // find the transaction record that matches the amount exactly
        Optional<TransactionRecord> txnRecord = records.stream().filter(txn -> txn.getDebitAsBigDecimal().equals(amountToRefund)).findFirst();

        // otherwise, find the first transaction that is large enough that we can refund it
        if (false == txnRecord.isPresent()) {
            txnRecord = records.stream().filter(txn -> txn.getDebitAsBigDecimal().compareTo(amountToRefund) >= 0).findFirst();
        }
        if (false == txnRecord.isPresent()) {
            throw new MissingUserDataException("Unable to find suitable transaction to refund against!");
        }
        cloudbedsScraper.processRefund(webClient, txnRecord.get(), amountToRefund, description);
    }

    /**
     * Does a AUTHORIZE/CAPTURE on the card details on the booking for the amount of the first
     * night.
     * 
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @throws IOException on i/o error
     */
    public synchronized void processHostelworldLateCancellationCharge( WebClient webClient, String reservationId ) throws IOException {
        LOGGER.info( "Processing payment for 1st night of booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        // check if we've paid anything
        if ( false == BigDecimal.ZERO.equals( cbReservation.getPaidValue() ) ) {
            LOGGER.info( "Booking has already been charged " + cbReservation.getPaidValue() );
            return;
        }

        if ( false == "canceled".equals( cbReservation.getStatus() ) ) {
            throw new UnrecoverableFault( "Only bookings at canceled can be charged." );
        }

        // check if card details exist in CB
        if ( false == cbReservation.isCardDetailsPresent() ) {
            throw new MissingUserDataException( "Missing card details found for reservation " + cbReservation.getReservationId() + ". Unable to continue." );
        }

        // should have credit card details at this point; attempt AUTHORIZE/CAPTURE
        BigDecimal amountToCharge = getLateCancellationAmountToCharge( cbReservation );
        cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, amountToCharge );
        cloudbedsScraper.addNote( webClient, reservationId, "Late cancellation. Successfully charged £"
                + cloudbedsScraper.getCurrencyFormat().format( amountToCharge ) );

        // send email if successful
        SendHostelworldLateCancellationEmailJob job = new SendHostelworldLateCancellationEmailJob();
        job.setReservationId( reservationId );
        job.setAmount( amountToCharge );
        job.setStatus( JobStatus.submitted );
        wordpressDAO.insertJob( job );
    }

    /**
     * Returns the late cancellation amount to charge for a booking.
     * 
     * @param cbReservation reservation being checked
     * @return value greater than 0
     */
    private BigDecimal getLateCancellationAmountToCharge( Reservation cbReservation ) {

        // august for HSH/RMB - charge the full amount
        if ( cbReservation.isCheckinDateInAugust() && false == wordpressDAO.getOption( "siteurl" ).contains( "castlerock" ) ) {
            if ( cbReservation.getBalanceDue().compareTo( BigDecimal.ZERO ) <= 0 ) {
                throw new IllegalStateException( "Some weirdness here. Outstanding balance must be greater than 0." );
            }
            return cbReservation.getBalanceDue();
        }

        // otherwise, for non-August or CRH (incl. August) - just the first night
        BigDecimal firstNightAmount = cbReservation.getRateFirstNight( gson );
        LOGGER.info( "First night due: " + firstNightAmount );
        if ( firstNightAmount.compareTo( BigDecimal.ZERO ) <= 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount must be greater than 0." );
        }
        if ( firstNightAmount.compareTo( cbReservation.getBalanceDue() ) > 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount exceeds balance due." );
        }
        return firstNightAmount;
    }

    /**
     * Attempts to charge the balance due for the given booking. If successful, send confirmation email.
     * If unsuccessful, send payment declined email (with payment link).
     *
     * @param webClient
     * @param reservationId
     * @throws IOException
     */
    public synchronized void chargeRemainingBalanceForBooking( WebClient webClient, String reservationId ) throws IOException {
        LOGGER.info( "Processing remaining balance for booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        if( cbReservation.containsNote( CHARGE_REMAINING_BALANCE_NOTE ) ) {
            LOGGER.info( "Already attempted to charge booking. Not attempting to try again." );
            return;
        }

        if( BigDecimal.ZERO.compareTo( cbReservation.getBalanceDue() ) >= 0 ) {
            LOGGER.info( "Nothing to do. Booking has an outstanding balance of " + cbReservation.getBalanceDue() );
            return;
        }

        if( false == Arrays.asList( "confirmed", "not_confirmed" ).contains( cbReservation.getStatus() ) ) {
            throw new UnrecoverableFault( "Booking is at unsupported status " + cbReservation.getStatus() );
        }

        try {
            // check if card details exist in CB
            if( false == cbReservation.isCardDetailsPresent() ) {
                throw new MissingUserDataException( "Missing card details found for reservation " + cbReservation.getReservationId() + ". Unable to continue." );
            }

            // should have credit card details at this point
            cloudbedsScraper.addArchivedNote( webClient, reservationId, CHARGE_REMAINING_BALANCE_NOTE );
            cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, cbReservation.getBalanceDue() );
            cloudbedsScraper.addArchivedNote( webClient, reservationId, "Successfully charged £"
                    + cloudbedsScraper.getCurrencyFormat().format( cbReservation.getBalanceDue() ) );
        }
        catch( RecordPaymentFailedException | PaymentPendingException | MissingUserDataException ex ) {
            SendTemplatedEmailJob job = new SendTemplatedEmailJob();
            autowireBeanFactory.autowireBean( job );
            Map<String, String> replaceMap = new HashMap<>();
            String paymentURL = cloudbedsService.generateUniquePaymentURL( reservationId, null );
            replaceMap.put( "\\[charge amount\\]", cloudbedsScraper.getCurrencyFormat().format( cbReservation.getBalanceDue() ) );
            replaceMap.put( "\\[payment URL\\]", paymentURL );
            job.setEmailTemplate( TEMPLATE_PAYMENT_DECLINED );
            job.setReservationId( reservationId );
            job.setReplacementMap( replaceMap );
            job.setStatus( JobStatus.submitted );
            wordpressDAO.insertJob( job );
            return;
        }

        // send email if successful
        SendTemplatedEmailJob job = new SendTemplatedEmailJob();
        autowireBeanFactory.autowireBean( job );
        Map<String, String> replaceMap = new HashMap<>();
        replaceMap.put( "\\[charge amount\\]", cloudbedsScraper.getCurrencyFormat().format( cbReservation.getBalanceDue() ) );
        replaceMap.put( "\\[last four digits\\]", cbReservation.getCreditCardLast4Digits() );
        job.setEmailTemplate( "Payment Successful" );
        job.setReservationId( reservationId );
        job.setReplacementMap( replaceMap );
        job.setNoteArchived( true );
        job.setStatus( JobStatus.submitted );
        wordpressDAO.insertJob( job );
    }

    /**
     * Retrieve card details for the given HW booking.
     * 
     * @param bookingRef e.g. HWL-555-123485758
     * @return full card details (not-null); amount is null
     * @throws IOException on page load error
     * @throws ParseException on parse error
     * @throws MissingUserDataException if card details not found
     */
    private CardDetails retrieveHWCardDetails( String bookingRef ) throws ParseException, IOException {
        try (WebClient hwlWebClient = context.getBean( "webClientForHostelworld", WebClient.class )) {
            return hostelworldScraper.getCardDetails( hwlWebClient, bookingRef );
        }
    }

    /**
     * Returns true iff amount is over the minimum chargeable amount.
     * @param amount amount starting with £
     * @return
     */
    public static boolean isChargeableAmount(String amount) {
        Pattern p = Pattern.compile("£(\\d+\\.?\\d*)");
        Matcher m = p.matcher(amount);
        if (false == m.find()) {
            throw new RuntimeException("Unable to parse amount " + amount);
        }
        return MINIMUM_CHARGE_AMOUNT.compareTo(new BigDecimal(m.group(1))) < 0;
    }

    /**
     * Processes a refund (on Cloudbeds) if card is still active. Otherwise, process refund on
     * Stripe and records it against the given Cloudbeds booking.
     * 
     * @param jobId job id (for idempotency)
     * @param refundTxnId primary key on StripeRefund
     * @throws IOException
     * @throws StripeException
     */
    public void processStripeRefund( int jobId, int refundTxnId ) throws IOException, StripeException {
        try (WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class )) {
            StripeRefund refund = wordpressDAO.fetchStripeRefund( refundTxnId );
            String refundAmount = cloudbedsScraper.getCurrencyFormat().format( refund.getAmount() );
            LOGGER.info( "Attempting to process refund for reservation " + refund.getReservationId() + " and txn " + refundTxnId + " for £" + refundAmount );
            Reservation res = cloudbedsScraper.getReservationRetry( webClient, refund.getReservationId() );
            TransactionRecord authTxn = cloudbedsScraper.getStripeTransaction( webClient, res, refund.getCloudbedsTxId() );
            String chargeId, refundDescription;

            // Stripe transaction was done through Cloudbeds
            if ( StringUtils.isNotBlank( authTxn.getGatewayAuthorization() ) ) {
                chargeId = authTxn.getGatewayAuthorization();
                refundDescription = refundTxnId + " (" + authTxn.getGatewayAuthorization() + "): " 
                        + authTxn.getOriginalDescription() + " x" + authTxn.getCardNumber();
            }
            else { // Stripe transaction was done via gateway and manually entered into Cloudbeds
                Matcher m = Pattern.compile( "VendorTxCode: (.*?)," ).matcher( authTxn.getNotes() );
                if ( false == m.find() ) {
                    throw new MissingUserDataException( "Unable to find VendorTxCode for refund " + refundTxnId );
                }
                String vendorTxCode = m.group( 1 );
                StripeTransaction originalTxn = wordpressDAO.fetchStripeTransaction( vendorTxCode );
                chargeId = originalTxn.getChargeId();
                refundDescription = refundTxnId + " (" + chargeId + "): " + vendorTxCode + " x"
                        + originalTxn.getLast4Digits() + " (" + originalTxn.getCardType() + ")";
            }

            if ( StringUtils.isBlank( chargeId ) ) {
                cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                        " for £" + refundAmount + ". Unable to find original transaction to refund." );
                throw new MissingUserDataException( "Unable to find original transaction to refund." );
            }

            else { // call Stripe server and record result manually
                String idempotentKey = wordpressDAO.getMandatoryOption( "hbo_vendor_prefix" )
                        + res.getIdentifier() + "-RF-" + jobId;
                LOGGER.info( "Attempting to process refund for charge " + chargeId );
                Stripe.apiKey = getStripeApiKey();
                try {
                    Refund stripeRefund = Refund.create( RefundCreateParams.builder()
                            .setCharge( chargeId )
                            .setAmount( refund.getAmountInBaseUnits() ).build(),
                            // set idempotency key so we can re-run safely in case of previous failure
                            new RequestOptionsBuilder().setIdempotencyKey( idempotentKey ).build() );
                    LOGGER.info( "Response: " + stripeRefund.toJson() );
                    wordpressDAO.updateStripeRefund( refundTxnId, chargeId, stripeRefund.toJson(), stripeRefund.getStatus() );

                    // record refund in Cloudbeds so we're in sync
                    if ( "succeeded".equals( stripeRefund.getStatus() ) ) {
                        cloudbedsScraper.addRefund( webClient, res, refund.getAmount(),
                                refundDescription + " refunded on Stripe. -RONBOT" );
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(),
                                "Refund completed for £" + refundAmount + "."
                                        + (refund.getDescription() == null ? "" : " " + refund.getDescription()) );
                        createSendRefundSuccessfulEmailJob( refund );
                    }
                    else if ( "pending".equals( stripeRefund.getStatus() ) ) {
                        LOGGER.info( "Refund pending" );
                        cloudbedsScraper.addRefund( webClient, res, refund.getAmount(),
                                refundDescription + " refund PENDING on Stripe. -RONBOT" );
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(),
                                "Refund PENDING (should usually be ok) for £" + refundAmount + "."
                                        + (refund.getDescription() == null ? "" : " " + refund.getDescription()) );
                        createSendRefundSuccessfulEmailJob( refund );
                    }
                    else {
                        LOGGER.error( "Unexpected response during refund: " + stripeRefund.toJson() );
                        wordpressDAO.updateStripeRefund( refundTxnId, chargeId, stripeRefund.toJson(), "failed" );
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                                " for £" + refundAmount + ". See logs for details." );
                    }
                }
                catch ( InvalidRequestException ex ) {
                    LOGGER.error( ex.getMessage() );
                    wordpressDAO.updateStripeRefund( refundTxnId, chargeId, ex.getMessage(), "failed" );
                    cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                            " for £" + refundAmount + ". " + ex.getMessage() );
                }
            }
        }
    }

    /**
     * Updates Cloudbeds for a booking (if successful) and creates a corresponding confirmation
     * email job.
     * 
     * @param vendorTxCode the unique ID for the transaction
     * @throws IOException
     * @throws StripeException
     */
    public synchronized void processStripePayment( String vendorTxCode ) throws IOException, StripeException {
        try (WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class )) {
            StripeTransaction txn = wordpressDAO.fetchStripeTransaction( vendorTxCode );
            Stripe.apiKey = getStripeApiKey();
            if ( txn.getReservationId() == null ) {
                // process stripe invoice payment
                PaymentIntent paymentIntent = txn.getPaymentIntent( gson );
                if ( "succeeded".equals( paymentIntent.getStatus() ) ) {
                    createSendStripePaymentConfirmationEmailJob( txn.getVendorTxCode() );
                }
                updateStripeTransactionWithPayment( txn, paymentIntent );
            }
            else {
                processStripeBookingPayment( webClient, txn );
            }
        }
    }

    /**
     * Updates Cloudbeds for a booking and creates a corresponding confirmation email job.
     * 
     * @param webClient
     * @param txn
     * @throws IOException
     * @throws StripeException
     */
    private void processStripeBookingPayment( WebClient webClient, StripeTransaction txn ) throws IOException, StripeException {
        LOGGER.info( "Attempting to process transaction for reservation " + txn.getReservationId() + " with vendor tx code " + txn.getVendorTxCode() );
        Reservation res = cloudbedsScraper.getReservationRetry( webClient, txn.getReservationId() );

        if ( cloudbedsScraper.isExistsPaymentWithVendorTxCode( webClient, res, txn.getVendorTxCode() ) ) {
            LOGGER.info( "We already have a transaction on the folio page with this vendor tx code: " + txn.getVendorTxCode() + ". Not adding it again..." );
        }
        else { // call Stripe server and record result manually
            PaymentIntent paymentIntent = txn.getPaymentIntent( gson );
            PaymentMethodDetails details = paymentIntent.getCharges().getData().get( 0 ).getPaymentMethodDetails();
            updateStripeTransactionWithPayment( txn, paymentIntent );

            if ( "succeeded".equals( paymentIntent.getStatus() ) ) {
                cloudbedsScraper.addPayment( webClient, res, txn.getPaymentAmount(),
                        String.format( "VendorTxCode: %s, Status: %s, Card Type: %s, Card Number: ************%s, Gateway: STRIPE",
                                txn.getVendorTxCode(), paymentIntent.getStatus(), details.getCard().getBrand(), details.getCard().getLast4() ) );
                cloudbedsScraper.addArchivedNote( webClient, txn.getReservationId(),
                        "Processed Stripe transaction for £" + txn.getPaymentAmount() + "." );
                createSendStripePaymentConfirmationEmailJob( txn.getVendorTxCode() );
                createArchiveAllTransactionNotesJob( res.getReservationId() );
            }
        }
    }

    /**
     * Updates the database entry with payment auth/failure details.
     * 
     * @param txn transaction to update
     * @param paymentIntent updated payment details
     */
    private void updateStripeTransactionWithPayment( StripeTransaction txn, PaymentIntent paymentIntent ) {
        Charge charge = paymentIntent.getCharges().getData().get( 0 );
        PaymentMethodDetails details = charge.getPaymentMethodDetails();
        Outcome outcome = charge.getOutcome();
        String authDetails = outcome.getSellerMessage();
        if ( charge.getFailureCode() != null || charge.getFailureMessage() != null ) {
            authDetails += ", " + charge.getFailureCode() + ": " + charge.getFailureMessage();
        }
        wordpressDAO.updateStripeTransaction( txn.getId(), paymentIntent.getStatus(), outcome.getType(),
                authDetails, charge.getId(), details.getCard().getBrand(), details.getCard().getLast4() );
    }

    /**
     * Reloads the Stripe refund status and response by looking up from the original auth
     * transaction.
     * 
     * @param refundTxnId PK of Refund table
     * @throws StripeException
     */
    public void refreshStripeRefundStatus( int refundTxnId ) throws StripeException {
        Stripe.apiKey = getStripeApiKey();
        StripeRefund refund = wordpressDAO.fetchStripeRefund( refundTxnId );
        LOGGER.info( "Updating refund status for reservation " + refund.getReservationId() + " and txn " + refundTxnId );
        Charge charge = Charge.retrieve( refund.getChargeId() );
        if ( charge.getRefunds().getData().size() == 0 ) {
            throw new MissingUserDataException( "Unable to find refund transaction." );
        }

        // if any refunds are not at succeeded, that status has precedence
        String[] status = new String[1];
        charge.getRefunds().getData().forEach( r -> {
            if ( false == "succeeded".equals( r.getStatus() ) || status[0] == null ) {
                status[0] = r.getStatus();
            }
        } );
        String jsonResponse = charge.getRefunds().getData().stream()
                .map( r -> r.toJson() ).collect( Collectors.joining( ",\n" ) );
        LOGGER.info( jsonResponse );
        wordpressDAO.updateStripeRefund( refundTxnId, charge.getId(), jsonResponse, status[0] );
    }

    /**
     * Send email on successful refund.
     * 
     * @param refund refund that has been processed
     */
    private void createSendRefundSuccessfulEmailJob( StripeRefund refund ) {
        LOGGER.info( "Creating SendRefundSuccessfulEmailJob for " + refund.getReservationId() );
        SendRefundSuccessfulEmailJob job = new SendRefundSuccessfulEmailJob();
        job.setReservationId( refund.getReservationId() );
        job.setAmount( refund.getAmount() );
        job.setTxnId( refund.getId() );
        job.setStatus( JobStatus.submitted );
        wordpressDAO.insertJob( job );
    }

    /**
     * Send email on successful payment.
     * 
     * @param vendorTxCode
     */
    private void createSendStripePaymentConfirmationEmailJob( String vendorTxCode ) {
        LOGGER.info( "Creating SendStripePaymentConfirmationEmailJob for vendor tx code " + vendorTxCode );
        SendStripePaymentConfirmationEmailJob j = new SendStripePaymentConfirmationEmailJob();
        j.setStatus( JobStatus.submitted );
        j.setVendorTxCode( vendorTxCode );
        wordpressDAO.insertJob( j );
    }

    /**
     * Creates ArchiveAllTransactionNotesJob for the given reservation.
     *
     * @param reservationId
     */
    private void createArchiveAllTransactionNotesJob( String reservationId ) {
        LOGGER.info( "Creating ArchiveAllTransactionNotesJob for reservation " + reservationId );
        ArchiveAllTransactionNotesJob j = new ArchiveAllTransactionNotesJob();
        j.setStatus( JobStatus.submitted );
        j.setReservationId( reservationId );
        wordpressDAO.insertJob( j );
    }

    /**
     * Queries Stripe for refund details.
     * 
     * @param refundTxnId PK of Refund table
     * @return non-null refund object
     * @throws StripeException
     */
    public Refund retrieveStripeRefund( int refundTxnId ) throws StripeException {
        Stripe.apiKey = getStripeApiKey();
        StripeRefund refund = wordpressDAO.fetchStripeRefund( refundTxnId );
        JsonElement refundElem = gson.fromJson( refund.getResponse(), JsonElement.class );
        String refundId = refundElem.getAsJsonObject().get( "id" ).getAsString();
        if ( refundId != null ) {
            return Refund.retrieve( refundId );
        }
        throw new MissingUserDataException( "Refund ID not found for txn " + refundTxnId );
    }

    private String getStripeApiKey() {
        return wordpressDAO.getMandatoryOption( "hbo_stripe_apikey" );
    }
}
