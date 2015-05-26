package com.macbackpackers.exceptions;

public class IncorrectNumberOfRecordsUpdatedException extends RuntimeException {

    private static final long serialVersionUID = 5777822002506768753L;

    public IncorrectNumberOfRecordsUpdatedException( String message ) {
        super( message );
    }

    public IncorrectNumberOfRecordsUpdatedException( Throwable cause ) {
        super( cause );
    }

    public IncorrectNumberOfRecordsUpdatedException( String message, Throwable cause ) {
        super( message, cause );
    }

}
