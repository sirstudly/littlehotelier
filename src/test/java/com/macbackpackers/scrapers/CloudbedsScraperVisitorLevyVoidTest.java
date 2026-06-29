
package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;

public class CloudbedsScraperVisitorLevyVoidTest {

    private static final String EXCLUSIVE_LABEL = "Edinburgh Visitor Levy 2026";
    private static final String INCLUSIVE_LABEL = "Edinburgh Visitor Levy (Inclusive)";

    @Test
    public void listVoidableVisitorLevyTransactions_returnsAdjustmentsBeforeTaxes() {
        TransactionRecord adjustment = evlTransaction( "adj-1", "adjustment", EXCLUSIVE_LABEL, false, true );
        TransactionRecord tax = evlTransaction( "tax-1", "tax", EXCLUSIVE_LABEL, false, true );

        List<TransactionRecord> voidable = CloudbedsScraper.listVoidableVisitorLevyTransactions(
                Arrays.asList( tax, adjustment ), EXCLUSIVE_LABEL, INCLUSIVE_LABEL );

        assertThat( voidable.size(), is( 2 ) );
        assertThat( voidable.get( 0 ).getId(), is( "adj-1" ) );
        assertThat( voidable.get( 1 ).getId(), is( "tax-1" ) );
    }

    @Test
    public void listVoidableVisitorLevyTransactions_excludesVoidedAndNonEvl() {
        TransactionRecord voided = evlTransaction( "voided", "tax", EXCLUSIVE_LABEL, true, true );
        TransactionRecord notVoidable = evlTransaction( "locked", "tax", EXCLUSIVE_LABEL, false, false );
        TransactionRecord vat = evlTransaction( "vat", "tax", "VAT", false, true );

        List<TransactionRecord> voidable = CloudbedsScraper.listVoidableVisitorLevyTransactions(
                Arrays.asList( voided, notVoidable, vat ), EXCLUSIVE_LABEL, INCLUSIVE_LABEL );

        assertThat( voidable.isEmpty(), is( true ) );
    }

    @Test
    public void listVoidableVisitorLevyTransactions_includesInclusiveLabel() {
        TransactionRecord inclusive = evlTransaction( "inc-1", "adjustment", INCLUSIVE_LABEL, false, true );

        List<TransactionRecord> voidable = CloudbedsScraper.listVoidableVisitorLevyTransactions(
                Arrays.asList( inclusive ), EXCLUSIVE_LABEL, INCLUSIVE_LABEL );

        assertThat( voidable.size(), is( 1 ) );
        assertThat( voidable.get( 0 ).getId(), is( "inc-1" ) );
    }

    private static TransactionRecord evlTransaction( String id, String type, String description,
            boolean voided, boolean canBeVoided ) {
        TransactionRecord record = new TransactionRecord();
        record.setId( id );
        record.setType( type );
        record.setDescription( description );
        record.setVoidFlag( voided ? "1" : "0" );
        record.setCanBeVoided( canBeVoided );
        return record;
    }
}
