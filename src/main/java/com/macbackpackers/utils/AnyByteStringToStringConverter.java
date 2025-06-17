package com.macbackpackers.utils;

import com.google.protobuf.ByteString;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
public class AnyByteStringToStringConverter implements GenericConverter {
    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton( new ConvertiblePair( ByteString.class, String.class ) );
    }

    @Override
    public Object convert( Object source, TypeDescriptor sourceType, TypeDescriptor targetType ) {
        if ( source instanceof ByteString ) {
            return ( (ByteString) source ).toStringUtf8();
        }
        return null;
    }
}