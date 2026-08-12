package com.macbackpackers.services;

import com.macbackpackers.SecretsManagerTestApp;
import com.macbackpackers.utils.AnyByteStringToStringConverter;
import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith( SpringExtension.class )
@SpringBootTest( classes = SecretsManagerTestApp.class )
@TestPropertySource( properties = {
        "spring.profiles.active=hsh"
} )
public class EdinburghVisitorLevyServiceTest {

    static {
        // Register the ByteString converter before Spring tries to resolve Secret Manager placeholders
        // This is essential for proper Secret Manager integration in tests
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() ).addConverter( new AnyByteStringToStringConverter() );
    }

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    EdinburghVisitorLevyService service;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    WebClient webClient;

    @Test
    public void testVoidAndResubmitLegacyEvlFolio() throws Exception {
        service.voidAndResubmitLegacyEvlFolio( webClient, "175315098" );
    }
}
