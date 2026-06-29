
package com.macbackpackers.beans.cloudbeds.responses;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

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
}
