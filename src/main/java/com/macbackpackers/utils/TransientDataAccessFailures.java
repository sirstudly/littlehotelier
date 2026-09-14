package com.macbackpackers.utils;

import java.io.EOFException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;

/**
 * Helpers for classifying flaky JDBC / pool connection failures (e.g. Tailscale blips).
 */
public final class TransientDataAccessFailures {

    private TransientDataAccessFailures() {
    }

    /**
     * Returns true if {@code t} or any cause looks like a dropped / closed JDBC connection
     * that is safe to retry.
     *
     * @param t throwable to inspect (may be null)
     * @return true when a later retry may succeed
     */
    public static boolean isTransientDbConnectionFailure( Throwable t ) {
        for ( Throwable cur = t ; cur != null ; cur = cur.getCause() ) {
            if ( cur instanceof TransientDataAccessException
                    || cur instanceof JDBCConnectionException
                    || cur instanceof SQLTransientConnectionException
                    || cur instanceof SQLRecoverableException
                    || cur instanceof SQLNonTransientConnectionException
                    || cur instanceof EOFException ) {
                return true;
            }
            if ( cur instanceof JpaSystemException && messageIndicatesClosedConnection( cur ) ) {
                return true;
            }
            if ( cur instanceof SQLException && messageIndicatesClosedConnection( cur ) ) {
                return true;
            }
            if ( cur instanceof org.hibernate.TransactionException && messageIndicatesClosedConnection( cur ) ) {
                return true;
            }
        }
        return false;
    }

    private static boolean messageIndicatesClosedConnection( Throwable t ) {
        String msg = t.getMessage();
        if ( msg == null ) {
            return false;
        }
        String lower = msg.toLowerCase();
        return lower.contains( "connection is closed" )
                || lower.contains( "unable to rollback" )
                || lower.contains( "communications link failure" )
                || lower.contains( "no operations allowed after connection closed" )
                || lower.contains( "connection reset" )
                || lower.contains( "broken pipe" );
    }
}
