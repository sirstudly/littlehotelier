package com.macbackpackers.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.hibernate.TransactionException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.JpaSystemException;

public class TransientDataAccessFailuresTest {

    @Test
    public void connectionClosedOnRollbackIsTransient() {
        SQLException closed = new SQLException( "Connection is closed" );
        TransactionException rollback = new TransactionException( "Unable to rollback against JDBC Connection", closed );
        JpaSystemException jpa = new JpaSystemException( rollback );

        assertTrue( TransientDataAccessFailures.isTransientDbConnectionFailure( jpa ) );
        assertTrue( TransientDataAccessFailures.isTransientDbConnectionFailure( rollback ) );
        assertTrue( TransientDataAccessFailures.isTransientDbConnectionFailure( closed ) );
    }

    @Test
    public void communicationsLinkFailureIsTransient() {
        assertTrue( TransientDataAccessFailures.isTransientDbConnectionFailure(
                new SQLException( "Communications link failure" ) ) );
    }

    @Test
    public void unrelatedFailuresAreNotTransient() {
        assertFalse( TransientDataAccessFailures.isTransientDbConnectionFailure( null ) );
        assertFalse( TransientDataAccessFailures.isTransientDbConnectionFailure(
                new IllegalArgumentException( "bad input" ) ) );
        assertFalse( TransientDataAccessFailures.isTransientDbConnectionFailure(
                new SQLException( "Duplicate entry for key" ) ) );
    }
}
