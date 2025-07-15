package com.macbackpackers.exceptions;

import java.io.Serial;

public class IORuntimeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -5497975387237958537L;

    public IORuntimeException( String message ) {
        super( message );
    }

    public IORuntimeException( Throwable cause ) {
        super( cause );
    }

    public IORuntimeException( String message, Throwable cause ) {
        super( message, cause );
    }

}
