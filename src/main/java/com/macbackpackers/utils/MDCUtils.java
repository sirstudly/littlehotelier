package com.macbackpackers.utils;

import org.slf4j.MDC;

import java.util.function.Supplier;

public class MDCUtils {

    public static <T> Supplier<T> wrapWithMDC(Supplier<T> supplier) {
        var contextMap = MDC.getCopyOfContextMap(); // Capture the current MDC context
        return () -> {
            if (contextMap != null) {
                MDC.setContextMap(contextMap); // Restore the MDC context in the worker thread
            }
            try {
                return supplier.get();
            } finally {
                MDC.clear(); // Clear MDC context after execution
            }
        };
    }
}
