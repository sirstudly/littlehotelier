
package com.macbackpackers.beans.cloudbeds.responses;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.number.BigDecimalCloseTo.closeTo;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;

public class TransactionRecordTest {

    @Test
    public void isVoided_handlesStringAndBooleanValues() {
        TransactionRecord record = new TransactionRecord();
        record.setVoidFlag( "0" );
        assertThat( record.isVoided(), is( false ) );

        record.setVoidFlag( "1" );
        assertThat( record.isVoided(), is( true ) );

        record.setVoidFlag( Boolean.TRUE );
        assertThat( record.isVoided(), is( true ) );
    }

    @Test
    public void isVoidable_requiresExplicitTrue() {
        TransactionRecord record = new TransactionRecord();
        record.setCanBeVoided( null );
        assertThat( record.isVoidable(), is( false ) );

        record.setCanBeVoided( true );
        assertThat( record.isVoidable(), is( true ) );
    }

    @Test
    public void getCreditAsBigDecimal_parsesSignedCurrencyValues() {
        TransactionRecord record = new TransactionRecord();
        record.setCredit( "£0.08" );
        assertThat( record.getCreditAsBigDecimal(), closeTo( new BigDecimal( "0.08" ), BigDecimal.ZERO ) );

        record.setCredit( "-£0.24" );
        assertThat( record.getCreditAsBigDecimal(), closeTo( new BigDecimal( "-0.24" ), BigDecimal.ZERO ) );
    }

    @Test
    public void getVisitorLevyContribution_returnsScaledCreditAmount() {
        TransactionRecord record = new TransactionRecord();
        record.setCredit( "-£0.170000" );
        assertThat( record.getVisitorLevyContribution(), closeTo( new BigDecimal( "-0.17" ), BigDecimal.ZERO ) );
    }

    @Test
    public void gson_deserializesEmptyPaidString() {
        String json = "{ \"id\": \"123\", \"paid\": \"\", \"description\": \"Edinburgh Visitor Levy 2026\", "
                + "\"type\": \"tax\", \"void\": false, \"can_be_voided\": true }";
        TransactionRecord record = new GsonBuilder()
                .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES )
                .create()
                .fromJson( json, TransactionRecord.class );

        assertThat( record.getId(), is( "123" ) );
        assertThat( record.getPaidAsBigDecimal(), is( nullValue() ) );
        assertThat( record.isVoidable(), is( true ) );
    }
}
