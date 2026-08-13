package com.macbackpackers.jobs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Single source of truth for queue priority when selecting the next job to process.
 * Lower values run first. Classes absent from the map use {@link #DEFAULT_PRIORITY}.
 */
public final class JobPriorities {

    public static final int DEFAULT_PRIORITY = 0;

    /** Non-default classname → priority. Keep comments briefly explaining why. */
    private static final Map<String, Integer> BY_CLASSNAME;

    static {
        Map<String, Integer> map = new LinkedHashMap<>();
        // Run ahead so staff edits to a booking aren't blocked waiting on the queue
        map.put( CalculateEdinburghVisitorLevyForBookingJob.class.getName(), -1 );
        map.put( StripeCheckoutCompletedJob.class.getName(), -1 );
        map.put( SendStripePaymentConfirmationEmailJob.class.getName(), -1 );

        // Demote; allocation scrapes can wait behind transactional work
        map.put( CloudbedsAllocationScraperWorkerJob.class.getName(), 99 );
        map.put( CreateAllocationScraperReportsJob.class.getName(), 99 );
        map.put( HousekeepingJob.class.getName(), 99 );
        BY_CLASSNAME = Collections.unmodifiableMap( map );
    }

    private JobPriorities() {
    }

    /**
     * @param jobClass job class (may be a Hibernate proxy subclass)
     * @return priority for that class, or {@link #DEFAULT_PRIORITY}
     */
    public static int forClass( Class<?> jobClass ) {
        if ( jobClass == null ) {
            return DEFAULT_PRIORITY;
        }
        return forClassName( jobClass.getName() );
    }

    /**
     * @param classname fully-qualified job classname (as stored in {@code wp_lh_jobs.classname})
     * @return priority for that classname, or {@link #DEFAULT_PRIORITY}
     */
    public static int forClassName( String classname ) {
        if ( classname == null ) {
            return DEFAULT_PRIORITY;
        }
        // Hibernate proxies append $HibernateProxy$… — strip to the entity classname
        int proxyIdx = classname.indexOf( "$HibernateProxy$" );
        if ( proxyIdx > 0 ) {
            classname = classname.substring( 0, proxyIdx );
        }
        return BY_CLASSNAME.getOrDefault( classname, DEFAULT_PRIORITY );
    }

    /**
     * Builds a MySQL {@code CASE} expression for ORDER BY from the same map used by
     * {@link #forClassName(String)}.
     *
     * @param classnameSqlExpr SQL expression yielding the classname column (e.g. {@code j.`classname`})
     * @return {@code CASE … ELSE 0 END}
     */
    public static String sqlCaseExpression( String classnameSqlExpr ) {
        StringBuilder sql = new StringBuilder( "CASE " ).append( classnameSqlExpr );
        // Stable order for predictable generated SQL
        for ( Map.Entry<String, Integer> e : new TreeMap<>( BY_CLASSNAME ).entrySet() ) {
            sql.append( " WHEN '" ).append( e.getKey() ).append( "' THEN " ).append( e.getValue() );
        }
        sql.append( " ELSE " ).append( DEFAULT_PRIORITY ).append( " END" );
        return sql.toString();
    }
}
