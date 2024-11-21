package com.macbackpackers.utils;

import org.slf4j.MDC;

import java.util.function.Function;

public class MDCUtils {

    /**
     * Used for parallel streams so we don't lose the MDC context on the fork/join stream pool. Note that because we
     * clear the MDC after each operation, the main thread may still lose the MDC after so you may still need to make
     * a copy of the MDC context prior to operating against the stream.
     *
     * @param fn  applied function
     * @param <T> anything
     * @param <R> anything
     * @return wrapped applied function
     */
    public static <T, R> Function<T, R> wrapWithMDC( Function<T, R> fn ) {
        var contextMap = MDC.getCopyOfContextMap(); // Capture the current MDC context
        return x -> {
            if ( contextMap != null ) {
                MDC.setContextMap( contextMap ); // Restore the MDC context in the worker thread
            }
            try {
                return fn.apply( x );
            }
            finally {
                MDC.clear(); // Clear MDC context after execution
            }
        };
    }
}
