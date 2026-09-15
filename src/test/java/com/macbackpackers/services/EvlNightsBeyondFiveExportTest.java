package com.macbackpackers.services;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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

import com.macbackpackers.SecretsManagerTestApp;
import com.macbackpackers.services.EvlNightsBeyondFiveExportService.ExportResult;
import com.macbackpackers.utils.AnyByteStringToStringConverter;

/**
 * Local harness for {@link EvlNightsBeyondFiveExportService}.
 *
 * <pre>
 * export JAVA_HOME=/Users/ron/Library/Java/JavaVirtualMachines/corretto-17.0.10/Contents/Home
 * export PATH="$JAVA_HOME/bin:$PATH"
 * MVN="/Users/ron/Library/Application Support/JetBrains/IntelliJIdea2026.1/plugins/maven/lib/maven3/bin/mvn"
 * bash "$MVN" -Dtest=EvlNightsBeyondFiveExportTest test
 * </pre>
 *
 * Switch property via {@code spring.profiles.active} ({@code test,crh} / {@code test,hsh} / {@code test,rmb}).
 */
@ExtendWith( SpringExtension.class )
@SpringBootTest( classes = SecretsManagerTestApp.class )
@TestPropertySource( properties = {
        "spring.profiles.active=test,rmb"
} )
public class EvlNightsBeyondFiveExportTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( EvlNightsBeyondFiveExportTest.class );

    static {
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() )
                .addConverter( new AnyByteStringToStringConverter() );
    }

    @Autowired
    private EvlNightsBeyondFiveExportService exportService;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    private WebClient webClient;

    @Test
    public void exportEvlNightsBeyondFiveRoomRevenue() throws Exception {
        String property = exportService.resolvePropertyCode();
        ExportResult result = exportService.exportToTempFile( webClient, property );
        File downloadsCopy = new File( System.getProperty( "user.home" ),
                "Downloads/evl_nights6plus_" + property + ".xlsx" );
        downloadsCopy.getParentFile().mkdirs();
        Files.copy( result.getFile().toPath(), downloadsCopy.toPath(), StandardCopyOption.REPLACE_EXISTING );
        if ( false == result.getFile().delete() ) {
            LOGGER.warn( "Could not delete temp file {}", result.getFile() );
        }
        LOGGER.info( "Wrote {} (rows={}, errors={})", downloadsCopy.getAbsolutePath(),
                result.getRowCount(), result.getErrorCount() );
    }
}
