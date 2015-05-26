package com.macbackpackers.exceptions;

public class UnrecoverableFault extends RuntimeException {

    private static final long serialVersionUID = 1220477433446430210L;

    public UnrecoverableFault( String message ) {
        super( message );
    }

    public UnrecoverableFault( Throwable cause ) {
        super( cause );
    }

    public UnrecoverableFault( String message, Throwable cause ) {
        super( message, cause );
    }

}
