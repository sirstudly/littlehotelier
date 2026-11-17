package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;

public class CloudbedsScraperAirbnbServiceFeeAdjustTest {

    @Test
    public void listVoidableAirbnbServiceFeeAdjustments_matchesRonbotNote() {
        TransactionRecord fee = adjustment( "Airbnb service fee -ronbot", true, false );
        TransactionRecord other = adjustment( "something else", true, false );
        TransactionRecord voided = adjustment( CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE, true, true );
        TransactionRecord notVoidable = adjustment( CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE, false, false );

        List<TransactionRecord> found = CloudbedsScraper.listVoidableAirbnbServiceFeeAdjustments(
                Arrays.asList( fee, other, voided, notVoidable ) );
        assertThat( found, hasSize( 1 ) );
        assertThat( found.get( 0 ), sameInstance( fee ) );
    }

    @Test
    public void listVoidableAirbnbServiceFeeAdjustments_emptyWhenNone() {
        assertThat( CloudbedsScraper.listVoidableAirbnbServiceFeeAdjustments( Collections.emptyList() ),
                empty() );
        assertThat( CloudbedsScraper.listVoidableAirbnbServiceFeeAdjustments( null ), empty() );
    }

    private static TransactionRecord adjustment( String notes, boolean canVoid, boolean voided ) {
        TransactionRecord t = new TransactionRecord();
        t.setType( "adjustment" );
        t.setDescription( "Room Revenue - Manual" );
        t.setNotes( notes );
        t.setCanBeVoided( canVoid );
        t.setVoidFlag( voided ? "1" : Boolean.FALSE );
        return t;
    }
}
