package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;

public class PaymentProcessorServicePrepaidRefundTest {

    private final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES )
            .create();

    @Test
    public void listCardBackedRefundPayments_ignoresBlankCardNumbersFromRateAndTaxRows() throws Exception {
        List<TransactionRecord> records = loadRefundModalRecords(
                "transactions_for_refund_modal_michael_pearson.json" );

        assertThat( records, hasSize( 4 ) );
        // Old buggy check: blank + last4 counted as two cards
        assertThat( records.stream().map( TransactionRecord::getCardNumber ).distinct().count(), is( 2L ) );

        List<TransactionRecord> cardPayments = PaymentProcessorService.listCardBackedRefundPayments( records );
        assertThat( cardPayments, hasSize( 1 ) );
        assertThat( cardPayments.get( 0 ).getCardNumber(), is( "6319" ) );
        assertThat( cardPayments.get( 0 ).getDebitAsBigDecimal(), is( new BigDecimal( "28.13" ) ) );
        assertThat( cardPayments.get( 0 ).getPaymentId(), is( "237987948" ) );
        assertThat( cardPayments.get( 0 ).getId(), is( "258927003725955" ) );
    }

    @Test
    public void listCardBackedRefundPayments_detectsTrulyDistinctCards() {
        TransactionRecord first = new TransactionRecord();
        first.setCardNumber( "6319" );
        first.setDebit( "£10.00" );
        TransactionRecord second = new TransactionRecord();
        second.setCardNumber( "5962" );
        second.setDebit( "£18.13" );
        TransactionRecord rate = new TransactionRecord();
        rate.setCardNumber( "" );
        rate.setDebit( "£0.00" );

        List<TransactionRecord> cardPayments = PaymentProcessorService.listCardBackedRefundPayments(
                Arrays.asList( rate, first, second ) );
        assertThat( cardPayments, hasSize( 2 ) );
        assertThat( cardPayments.stream().map( TransactionRecord::getCardNumber ).distinct().count(), is( 2L ) );
    }

    private List<TransactionRecord> loadRefundModalRecords( String resource ) throws Exception {
        try ( InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream( resource ), StandardCharsets.UTF_8 ) ) {
            JsonObject rpt = gson.fromJson( reader, JsonObject.class );
            return StreamSupport.stream( rpt.get( "records" ).getAsJsonArray().spliterator(), false )
                    .map( r -> gson.fromJson( r, TransactionRecord.class ) )
                    .collect( Collectors.toList() );
        }
    }
}
