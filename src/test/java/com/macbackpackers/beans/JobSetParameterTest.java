package com.macbackpackers.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class JobSetParameterTest {

    @Test
    public void setParameterUpdatesExistingValueInsteadOfIgnoring() {
        Job job = new Job();
        job.setParameter( "retry_count_remaining", "5" );
        assertEquals( "5", job.getParameter( "retry_count_remaining" ) );
        assertEquals( 1, job.getParameters().size() );

        job.setParameter( "retry_count_remaining", "4" );
        assertEquals( "4", job.getParameter( "retry_count_remaining" ) );
        assertEquals( 1, job.getParameters().size() );

        job.setParameter( "retry_count_remaining", "0" );
        assertEquals( "0", job.getParameter( "retry_count_remaining" ) );
        assertEquals( 1, job.getParameters().size() );
    }
}
