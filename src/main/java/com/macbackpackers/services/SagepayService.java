
package com.macbackpackers.services;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.Locale;

import javax.mail.MessagingException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.SagepayRefund;
import com.macbackpackers.beans.SagepayTransaction;
import com.macbackpackers.beans.cloudbeds.responses.EmailTemplateInfo;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.PaymentNotAuthorizedException;
import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.jobs.SendRefundSuccessfulEmailJob;
import com.macbackpackers.jobs.SendSagepayPaymentConfirmationEmailJob;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Service for processing Sagepay transactions.
 */
@Service
public class SagepayService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${sagepay.integration.key}" )
    private String SAGEPAY_INTEGRATION_KEY;

    @Value( "${sagepay.integration.password}" )
    private String SAGEPAY_INTEGRATION_PASSWORD;

    @Value( "${sagepay.integration.transactions.url}" )
    private String SAGEPAY_INTEGRATION_TRANSACTIONS_URL;

    @Autowired
    @Qualifier( "gsonForSagepay" )
    private Gson gson;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WordPressDAO wordpressDAO;

    @Autowired
    private GmailService gmailService;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    /**
     * Process a refund and update corresponding booking.
     * 
     * @param jobId job identifier (used in refund vendorTxCode so we don't refund more than once on
     *            failure)
     * @param refundId primary key on SagepayRefund
     * @throws IOException on i/o error
     * @throws RecordPaymentFailedException on error updating cloudbeds
     */
    public synchronized void processSagepayRefund( int jobId, int refundId ) throws IOException, RecordPaymentFailedException {

        try (WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class )) {
            SagepayRefund refund = wordpressDAO.fetchSagepayRefund( refundId );
            String refundAmount = cloudbedsScraper.getCurrencyFormat().format( refund.getAmount() );
            LOGGER.info( "Attempting to process refund for reservation " + refund.getReservationId() + " and id " + refundId + " for £" + refundAmount );
            Reservation res = cloudbedsScraper.getReservationRetry( webClient, refund.getReservationId() );
            SagepayTransaction authTxn = wordpressDAO.fetchSagepayTransaction( refund.getAuthVendorTxCode() );

            // attempt refund against Sagepay
            String rfVendorTxCode = wordpressDAO.getMandatoryOption( "hbo_sagepay_vendor_prefix" )
                    + res.getIdentifier() + "-RF-" + jobId;
            Page redirectPage = webClient.getPage( createRefundSagepayRequest( rfVendorTxCode,
                    authTxn.getVpsTxId(), refund.getAmountInBaseUnits(), "Refund requested" ) );
            String responseJson = redirectPage.getWebResponse().getContentAsString();
            LOGGER.info( responseJson );

            // before we do anything else, persist response to DB
            String status = null, statusDetail = null, txnId = null;
            try {
                JsonElement response = gson.fromJson( responseJson, JsonElement.class );
                status = getValueIfPresent( response, "status" );
                statusDetail = getValueIfPresent( response, "statusDetail" );
                txnId = getValueIfPresent( response, "transactionId" );
            }
            catch ( JsonSyntaxException ex ) {
                LOGGER.error( "Error parsing response as JSON" );
            }
            wordpressDAO.updateSagepayRefund( refundId, rfVendorTxCode, responseJson, status, statusDetail, txnId );

            // record refund in Cloudbeds
            if ( "Ok".equals( status ) ) {
                cloudbedsScraper.addRefund( webClient, res, refund.getAmount(), "(" + refundId + ") VendorTxCode: "
                        + authTxn.getVendorTxCode() + " refunded on Sagepay. -RONBOT" );
            }
            else {
                cloudbedsScraper.addNote( webClient, authTxn.getReservationId(), "Failed to refund transaction " +
                        authTxn.getVendorTxCode() + " for £" + refundAmount + ". See logs for details." );
                throw new PaymentNotAuthorizedException( "Failed to refund transaction.", redirectPage.getWebResponse() );
            }

            cloudbedsScraper.addNote( webClient, refund.getReservationId(),
                    "Refund completed for £" + refundAmount + "."
                            + (refund.getDescription() == null ? "" : " " + refund.getDescription()) );

            LOGGER.info( "Creating SendRefundSuccessfulEmailJob for " + res.getIdentifier() );
            SendRefundSuccessfulEmailJob j = new SendRefundSuccessfulEmailJob();
            j.setStatus( JobStatus.submitted );
            j.setReservationId( refund.getReservationId() );
            j.setTxnId( refund.getId() );
            j.setAmount( refund.getAmount() );
            wordpressDAO.insertJob( j );
        }
    }

    /**
     * Returns the value from the root of the JsonElement if present.
     * 
     * @param elem root JSON element
     * @param memberName root field name to query
     * @return value if present or null if not
     */
    private String getValueIfPresent( JsonElement elem, String memberName ) {
        if ( elem == null ) return null;
        JsonElement record = elem.getAsJsonObject().get( memberName );
        return record == null ? null : record.getAsString();
    }

    /**
     * Creates a web request for issuing a refund.
     * 
     * @param vendorTxCode original vendorTxCode authorization to refund
     * @param vpsTxnId authorization transaction id to refund
     * @param amountPence amount to refund in pence
     * @param description transaction description (sent to Sagepay)
     * @return web request
     * @throws IOException
     */
    private WebRequest createRefundSagepayRequest( String vendorTxCode, String vpsTxnId, long amountPence, String description ) throws IOException {
        WebRequest webRequest = new WebRequest( new URL( SAGEPAY_INTEGRATION_TRANSACTIONS_URL ), HttpMethod.POST );
        webRequest.setAdditionalHeader( "Content-Type", "application/json; charset=UTF-8" );
        webRequest.setAdditionalHeader( "Authorization", "Basic " +
                Base64.getEncoder().encodeToString( (SAGEPAY_INTEGRATION_KEY + ":" + SAGEPAY_INTEGRATION_PASSWORD).getBytes() ) );
        webRequest.setCharset( StandardCharsets.UTF_8 );

        JsonObject refund = new JsonObject();
        refund.addProperty( "transactionType", "Refund" );
        refund.addProperty( "vendorTxCode", vendorTxCode );
        refund.addProperty( "referenceTransactionId", vpsTxnId.replaceAll( "\\{", "" ).replaceAll( "\\}", "" ) );
        refund.addProperty( "amount", amountPence );
        refund.addProperty( "currency", "GBP" );
        refund.addProperty( "description", description );
        webRequest.setRequestBody( gson.toJson( refund ) );
        return webRequest;
    }

    /**
     * Record a completed Sagepay transaction in Cloudbeds. Either add payment info or add a note
     * (in case of a decline).
     * 
     * @param id PK of wp_sagepay_tx_auth
     * @throws RecordPaymentFailedException on record payment failure
     * @throws IOException on i/o error
     * @throws MessagingException 
     */
    public synchronized void processSagepayTransaction( int id ) throws IOException, RecordPaymentFailedException, MessagingException {

        try (WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class )) {
            SagepayTransaction txn = wordpressDAO.fetchSagepayTransaction( id );
            
            // INVOICE transaction
            if ( txn.getBookingReference() == null ) {
                try {
                    LOGGER.info( "No booking reference. Processing as invoice payment..." );
                    if ( "OK".equals( txn.getAuthStatus() ) ) {
                        LOGGER.info( "Successful payment. Sending email to " + txn.getEmail() );
                        sendSagepayPaymentConfirmationEmail( webClient, txn );
                    }
                    else {
                        LOGGER.info( "Transaction status is " + txn.getAuthStatus() + ". Nothing to do..." );
                    }
                }
                finally {
                    wordpressDAO.updateSagepayTransactionProcessedDate( id );
                }
                return;
            }

            LOGGER.info( "Processing transaction with status " + txn.getAuthStatus() + " for booking ref " + txn.getBookingReference() );
            // otherwise BOOKING transaction
            switch ( txn.getAuthStatus() ) {
                case "OK":
                    // check if payment already exists
                    Reservation res = cloudbedsScraper.getReservationRetry( webClient, txn.getReservationId() );
                    if( cloudbedsScraper.isExistsPaymentWithVendorTxCode( webClient, res, txn.getVendorTxCode() ) ) {
                        LOGGER.info( "Transaction " + txn.getVendorTxCode() + " has already been processed. Nothing to do." );
                    }
                    else {
                        cloudbedsScraper.addPayment( webClient, res, txn.getMappedCardType(), txn.getPaymentAmount(),
                                String.format( "VendorTxCode: %s, Status: %s, Detail: %s, VPS Auth Code: %s, "
                                        + "Card Type: %s, Card Number: ************%s, Auth Code: %s",
                                        txn.getVendorTxCode(), txn.getAuthStatus(), txn.getAuthStatusDetail(), txn.getVpsAuthCode(),
                                        txn.getCardType(), txn.getLastFourDigits(), txn.getBankAuthCode() ) );
                        LOGGER.info( "Creating SendSagepayPaymentConfirmationEmailJob for " + res.getIdentifier() );
                        SendSagepayPaymentConfirmationEmailJob j = new SendSagepayPaymentConfirmationEmailJob();
                        j.setStatus( JobStatus.submitted );
                        j.setReservationId( txn.getReservationId() );
                        j.setSagepayTxnId( id );
                        wordpressDAO.insertJob( j );
                    }
                    wordpressDAO.updateSagepayTransactionProcessedDate( id );
                    break;

                case "NOTAUTHED":
                    cloudbedsScraper.addNote( webClient, txn.getReservationId(),
                            String.format( "The Sage Pay gateway could not authorise the transaction because "
                                    + "the details provided by the customer were incorrect, or "
                                    + "insufficient funds were available.\n"
                                    + "VendorTxCode: %s\nStatus: %s\nDetail: %s\nCard Type: %s\nCard Number: ************%s\nDecline Code: %s",
                                    txn.getVendorTxCode(), txn.getAuthStatus(), txn.getAuthStatusDetail(),
                                    txn.getCardType(), txn.getLastFourDigits(), txn.getBankDeclineCode() ) );
                    wordpressDAO.updateSagepayTransactionProcessedDate( id );
                    break;

                case "PENDING":
                    cloudbedsScraper.addNote( webClient, txn.getReservationId(),
                            String.format( "Pending Sagepay transaction %s: This will be updated "
                                    + "by Sage Pay when we receive a notification from PPRO.",
                                    txn.getVendorTxCode() ) );
                    wordpressDAO.updateSagepayTransactionProcessedDate( id );
                    break;

                case "ABORT":
                    cloudbedsScraper.addNote( webClient, txn.getReservationId(),
                            String.format( "Aborted Sagepay transaction %s for %s",
                                    txn.getVendorTxCode(),
                                    NumberFormat.getCurrencyInstance(Locale.UK).format( txn.getPaymentAmount() ) ) );
                    wordpressDAO.updateSagepayTransactionProcessedDate( id );
                    break;

                case "REJECTED":
                    cloudbedsScraper.addNote( webClient, txn.getReservationId(),
                            String.format( "The Sage Pay System rejected transaction %s because of the fraud "
                                    + "screening rules (e.g. AVS/CV2 or 3D Secure) you have set on your account.",
                                    txn.getVendorTxCode() ) );
                    wordpressDAO.updateSagepayTransactionProcessedDate( id );
                    break;

                case "ERROR":
                    cloudbedsScraper.addNote( webClient, txn.getReservationId(),
                            String.format( "A problem occurred at Sage Pay which prevented transaction %s registration. This is not normal! Contact SagePay!",
                                    txn.getVendorTxCode() ) );
                    LOGGER.error( "Unexpected SagePay error for transaction " + txn.getVendorTxCode() );
                    wordpressDAO.updateSagepayTransactionProcessedDate( id );
                    break;

                default:
                    throw new UnrecoverableFault( String.format( "Unsupported AUTH status %s for transaction %s", txn.getAuthStatus(), txn.getVendorTxCode() ) );
            }
        }
    }

    /**
     * Sends a payment confirmation email using the given template and transaction. This differs
     * from the other method in that it is not tied to a current booking.
     * 
     * @param webClient web client instance to use
     * @param txn successful sagepay transaction
     * @throws IOException
     * @throws MessagingException
     */
    public void sendSagepayPaymentConfirmationEmail( WebClient webClient, SagepayTransaction txn ) throws IOException, MessagingException {
        EmailTemplateInfo template = cloudbedsScraper.getSagepayPaymentConfirmationEmailTemplate( webClient );
        gmailService.sendEmailCcSelf( txn.getEmail(), txn.getFirstName() + " " + txn.getLastName(), template.getSubject(),
                IOUtils.resourceToString( "/sth_email_template.html", StandardCharsets.UTF_8 )
                        .replaceAll( "__IMG_ALIGN__", template.getTopImageAlign() )
                        .replaceAll( "__IMG_SRC__", template.getTopImageSrc() )
                        .replaceAll( "__EMAIL_CONTENT__", template.getEmailBody()
                                .replaceAll( "\\[vendor tx code\\]", txn.getVendorTxCode() )
                                .replaceAll( "\\[payment total\\]", cloudbedsScraper.getCurrencyFormat().format( txn.getPaymentAmount() ) )
                                .replaceAll( "\\[card type\\]", txn.getCardType() )
                                .replaceAll( "\\[last 4 digits\\]", txn.getLastFourDigits() ) ) );
    }

}
