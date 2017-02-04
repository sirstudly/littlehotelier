package com.macbackpackers.exceptions;

public class MissingUserDataException extends RuntimeException {

    private static final long serialVersionUID = -4064316112394492013L;

    public MissingUserDataException( String message ) {
        super( message );
    }

    public MissingUserDataException( Throwable cause ) {
        super( cause );
    }

    public MissingUserDataException( String message, Throwable cause ) {
        super( message, cause );
    }

}
