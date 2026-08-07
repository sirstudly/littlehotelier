package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;

public class CloudbedsScraperVisitorLevyVoidTest {

    private static final String EXCLUSIVE_LABEL = "Edinburgh Visitor Levy 2026";
    private static final String INCLUSIVE_LABEL = "Edinburgh Visitor Levy (Inclusive)";
    private static final String FUTURE_EXCLUSIVE_LABEL = "Edinburgh Visitor Levy 2027";

    @Test
    public void listVoidableVisitorLevyTransactions_returnsAdjustmentsBeforeTaxes() {
        TransactionRecord adjustment = evlTransaction( "adj-1", "adjustment", EXCLUSIVE_LABEL, false, true );
        TransactionRecord tax = evlTransaction( "tax-1", "tax", EXCLUSIVE_LABEL, false, true );

        List<TransactionRecord> voidable = CloudbedsScraper.listVoidableVisitorLevyTransactions(
                Arrays.asList( tax, adjustment ) );

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
                Arrays.asList( voided, notVoidable, vat ) );

        assertThat( voidable.isEmpty(), is( true ) );
    }

    @Test
    public void listVoidableVisitorLevyTransactions_includesInclusiveLabel() {
        TransactionRecord inclusive = evlTransaction( "inc-1", "adjustment", INCLUSIVE_LABEL, false, true );

        List<TransactionRecord> voidable = CloudbedsScraper.listVoidableVisitorLevyTransactions(
                Arrays.asList( inclusive ) );

        assertThat( voidable.size(), is( 1 ) );
        assertThat( voidable.get( 0 ).getId(), is( "inc-1" ) );
    }

    @Test
    public void listVoidableVisitorLevyTransactions_matchesFutureYearLabel() {
        TransactionRecord futureYear = evlTransaction( "tax-2027", "tax", FUTURE_EXCLUSIVE_LABEL, false, true );

        List<TransactionRecord> voidable = CloudbedsScraper.listVoidableVisitorLevyTransactions(
                Arrays.asList( futureYear ) );

        assertThat( voidable.size(), is( 1 ) );
        assertThat( voidable.get( 0 ).getId(), is( "tax-2027" ) );
    }

    @Test
    public void findVoidableVisitorLevyTransactionForDelta_matchesReductionAdjustmentWhenDeltaPositive() {
        TransactionRecord adjustment = evlTransaction( "adj-1", "adjustment", EXCLUSIVE_LABEL, false, true );
        adjustment.setCredit( "-£0.24" );

        Optional<TransactionRecord> match = CloudbedsScraper.findVoidableVisitorLevyTransactionForDelta(
                Arrays.asList( adjustment ), new BigDecimal( "0.24" ) );

        assertThat( match.isPresent(), is( true ) );
        assertThat( match.get().getId(), is( "adj-1" ) );
    }

    @Test
    public void findVoidableVisitorLevyTransactionForDelta_matchesAddedTaxWhenDeltaNegative() {
        TransactionRecord tax = evlTransaction( "tax-1", "tax", EXCLUSIVE_LABEL, false, true );
        tax.setCredit( "£0.24" );

        Optional<TransactionRecord> match = CloudbedsScraper.findVoidableVisitorLevyTransactionForDelta(
                Arrays.asList( tax ), new BigDecimal( "-0.24" ) );

        assertThat( match.isPresent(), is( true ) );
        assertThat( match.get().getId(), is( "tax-1" ) );
    }

    @Test
    public void findVoidableVisitorLevyTransactionForDelta_returnsEmptyWhenNoExactMatch() {
        TransactionRecord tax = evlTransaction( "tax-1", "tax", EXCLUSIVE_LABEL, false, true );
        tax.setCredit( "£0.12" );

        Optional<TransactionRecord> match = CloudbedsScraper.findVoidableVisitorLevyTransactionForDelta(
                Arrays.asList( tax ), new BigDecimal( "0.24" ) );

        assertThat( match.isPresent(), is( false ) );
    }

    @Test
    public void findVoidableVisitorLevyTransactionForDelta_returnsMostRecentWhenMultipleMatch() {
        TransactionRecord older = evlTransaction( "adj-old", "adjustment", EXCLUSIVE_LABEL, false, true );
        older.setCredit( "-£0.24" );
        older.setDatetimeTransaction( "01/06/2026 10:00:00" );
        TransactionRecord newer = evlTransaction( "adj-new", "adjustment", EXCLUSIVE_LABEL, false, true );
        newer.setCredit( "-£0.24" );
        newer.setDatetimeTransaction( "02/06/2026 10:00:00" );

        Optional<TransactionRecord> match = CloudbedsScraper.findVoidableVisitorLevyTransactionForDelta(
                Arrays.asList( older, newer ), new BigDecimal( "0.24" ) );

        assertThat( match.isPresent(), is( true ) );
        assertThat( match.get().getId(), is( "adj-new" ) );
    }

    @Test
    public void findVoidableVisitorLevyTransactionForDelta_excludesVoidedAndNonEvl() {
        TransactionRecord voided = evlTransaction( "voided", "adjustment", EXCLUSIVE_LABEL, true, true );
        voided.setCredit( "-£0.24" );
        TransactionRecord notVoidable = evlTransaction( "locked", "adjustment", EXCLUSIVE_LABEL, false, false );
        notVoidable.setCredit( "-£0.24" );
        TransactionRecord vat = evlTransaction( "vat", "tax", "VAT", false, true );
        vat.setCredit( "-£0.24" );

        Optional<TransactionRecord> match = CloudbedsScraper.findVoidableVisitorLevyTransactionForDelta(
                Arrays.asList( voided, notVoidable, vat ), new BigDecimal( "0.24" ) );

        assertThat( match.isPresent(), is( false ) );
    }

    @Test
    public void findVisitorLevyTaxId_resolvesExclusiveAndInclusiveBySubstring() {
        JsonObject propertyContent = propertyContentWithTaxes(
                tax( "1", "VAT" ),
                tax( "824186", "Edinburgh Visitor Levy 2026" ),
                tax( "824360", "Edinburgh Visitor Levy (Inclusive)" ) );

        assertThat( CloudbedsScraper.findVisitorLevyTaxId( propertyContent, false ).get(), is( "824186" ) );
        assertThat( CloudbedsScraper.findVisitorLevyTaxId( propertyContent, true ).get(), is( "824360" ) );
    }

    @Test
    public void findVisitorLevyTaxId_prefersActiveNameOverBeforeRename() {
        JsonObject propertyContent = propertyContentWithTaxes(
                tax( "old", "Edinburgh Visitor Levy 2026 (Before 05/07/2026 12:51 PM)" ),
                tax( "active", "Edinburgh Visitor Levy 2027" ) );

        assertThat( CloudbedsScraper.findVisitorLevyTaxId( propertyContent, false ).get(), is( "active" ) );
    }

    @Test
    public void findVisitorLevyTaxId_matchesInclusiveWithYearInName() {
        JsonObject propertyContent = propertyContentWithTaxes(
                tax( "excl", "Edinburgh Visitor Levy 2026" ),
                tax( "incl", "Edinburgh Visitor Levy 2026 (Inclusive)" ) );

        assertThat( CloudbedsScraper.findVisitorLevyTaxId( propertyContent, true ).get(), is( "incl" ) );
    }

    private static JsonObject propertyContentWithTaxes( JsonObject... taxes ) {
        JsonObject propertyContent = new JsonObject();
        JsonArray taxArray = new JsonArray();
        for ( JsonObject tax : taxes ) {
            taxArray.add( tax );
        }
        propertyContent.add( "taxes", taxArray );
        return propertyContent;
    }

    private static JsonObject tax( String id, String englishName ) {
        JsonObject tax = new JsonObject();
        tax.addProperty( "id", id );
        JsonObject nameLangs = new JsonObject();
        nameLangs.addProperty( "en", englishName );
        tax.add( "name_langs", nameLangs );
        return tax;
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
