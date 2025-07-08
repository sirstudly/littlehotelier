package com.macbackpackers.utils;

import org.slf4j.MDC;

import java.util.Map;
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

    /**
     * Wraps a task with MDC context propagation for thread pool execution.
     * This ensures that logging in child threads includes the parent thread's MDC context.
     *
     * @param mdcContext the MDC context to propagate
     * @param task       the task to execute
     * @return the wrapped task
     */
    public static <T> java.util.concurrent.Callable<T> wrapWithMDC( Map<String, String> mdcContext, java.util.concurrent.Callable<T> task ) {
        return () -> {
            MDC.setContextMap( mdcContext );
            try {
                return task.call();
            }
            finally {
                MDC.clear();
            }
        };
    }
}
