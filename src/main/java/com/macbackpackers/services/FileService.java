
package com.macbackpackers.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

@Service
public class FileService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private static final String COOKIE_FILE = "cookie.file";
    private static final FastDateFormat DATETIME_STANDARD = FastDateFormat.getInstance( "MMM dd, yyyy h:mm:ss a" );
    
    @Autowired
    @Qualifier("gsonForCloudbeds")
    private Gson gson;

    /**
     * Attempts to acquire an exclusive lock on the given file. Creates the file first if it doesn't
     * exist. This method will *not* block if it cannot acquire the lock.
     * 
     * @param file the lock file
     * @return the file lock (or null if lock could not be acquired)
     * @throws IOException if file could not be created
     */
    public FileLock lockFile( File file ) throws IOException {

        FileLock lock = null;

        LOGGER.info( "attempting file lock on " + file.getName() );

        if ( false == file.exists() ) {
            file.createNewFile();
        }

        try {

            // Get a file channel for the file
            //File file = new File("filename");
            FileChannel channel = new RandomAccessFile( file, "rw" ).getChannel();

            // Use the file channel to create a lock on the file.
            // This method blocks until it can retrieve the lock.
            //FileLock lock = channel.lock();

            /*
               use channel.lock OR channel.tryLock();
            */

            // Try acquiring the lock without blocking. This method returns
            // null or throws an exception if the file is already locked.
            try {
                lock = channel.tryLock();
            }
            catch ( OverlappingFileLockException e ) {
                // File is already locked in this thread or virtual machine
                LOGGER.info( "File is already locked in this thread or virtual machine" );
            }

            if ( lock == null ) {
                LOGGER.info( "could not acquire file lock" );
            }
            else {
                LOGGER.info( "file lock acquired" );
            }

            // Release the lock
            //            if( lock != null ) {
            //                lock.release();
            //            }
            //            
            //            // Close the file
            //            channel.close();

        }
        catch ( IOException e ) {

            LOGGER.error( "I/O error", e );

        }
        return lock;
    }

    /**
     * Loads cookies written from the previous session if found.
     * 
     * @param webClient
     * @throws IOException on read error
     */
    public void loadCookiesFromFile( WebClient webClient ) throws IOException {
        loadCookiesFromFile( webClient, COOKIE_FILE );
    }

    /**
     * Loads cookies written from the previous session if found.
     * 
     * @param webClient
     * @param filename name of cookie file
     * @throws IOException on read error
     */
    public void loadCookiesFromFile( WebClient webClient, String filename ) throws IOException {

        File file = new File( filename );
        if ( file.exists() ) {
            LOGGER.info( "loading cookies from file " + filename );
            ObjectInputStream in = new ObjectInputStream( new FileInputStream( file ) );
            try {
                @SuppressWarnings( "unchecked" )
                Set<Cookie> cookies = (Set<Cookie>) in.readObject();

                for ( Iterator<Cookie> i = cookies.iterator() ; i.hasNext() ; ) {
                    webClient.getCookieManager().addCookie( i.next() );
                }
            }
            catch ( ClassNotFoundException e ) {
                throw new IOException( "Unable to read cookie!", e );
            }
            finally {
                in.close();
            }
        }
        else {
            LOGGER.info( "No cookies file found." );
        }
    }

    /**
     * Loads cookies written from the previous session if found.
     * 
     * @param webDriver
     * @param filename name of cookie file
     * @throws IOException on read error
     */
    public void loadCookiesFromFile( WebDriver webDriver, String filename ) throws IOException {

        File file = new File( filename );
        if ( file.exists() ) {
            LOGGER.info( "loading cookies from file " + filename );
            try (FileInputStream fis = new FileInputStream( file )) {
                JsonArray cookies = gson.fromJson( IOUtils.toString( fis, StandardCharsets.UTF_8 ), JsonArray.class );
                StreamSupport.stream( cookies.spliterator(), false )
                        .map( c -> c.getAsJsonObject() )
                        .forEach( c -> webDriver.manage().addCookie( new org.openqa.selenium.Cookie(
                                c.get( "name" ).getAsString(),
                                c.get( "value" ).getAsString(),
                                c.get( "domain" ).getAsString(),
                                c.get( "path" ).getAsString(),
                                c.has( "expiry" ) ? parseDate( c.get( "expiry" ).getAsString() ) : null,
                                c.get( "is_secure" ).getAsBoolean(),
                                c.get( "is_http_only" ).getAsBoolean() ) ) );
            }
        }
        else {
            LOGGER.info( "No cookies file found." );
        }
    }

    private Date parseDate( String date ) {
        try {
            return DATETIME_STANDARD.parse( date );
        }
        catch ( ParseException e ) {
            throw new RuntimeException( e );
        }
    }

    /**
     * Taken from Chrome (Under Developer Tools, Application, Cookies). Having trouble logging into
     * LH using just username and password.
     * 
     * @param webClient the web client
     * @param sessionId a pre-logged in session
     */
    public void addLittleHotelierSessionCookie( WebClient webClient, String sessionId ) {
        webClient.getCookieManager().addCookie( new Cookie(
                "app.littlehotelier.com", "_littlehotelier_session", sessionId ) );
    }

    /**
     * Deserialise the previously serialised object from file if found.
     * 
     * @param filename name of file
     * @param clazz the type of object it was
     * @throws IOException on read error
     */
    public <T> T deserializeObjectFromFile( String filename, Class<T> clazz ) throws IOException {

        File file = new File( filename );
        LOGGER.info( "loading object from disk " + filename );
        if ( file.exists() ) {
            ObjectInputStream in = new ObjectInputStream( new FileInputStream( file ) );
            try {
                return clazz.cast( in.readObject() );
            }
            catch ( ClassNotFoundException e ) {
                throw new IOException( "Unable to read object!", e );
            }
            finally {
                in.close();
            }
        }
        throw new FileNotFoundException( "File " + filename + " does not exist!" );
    }

    /**
     * Serialises the current cookies to disk.
     * 
     * @throws IOException on serialisation error
     */
    public void writeCookiesToFile( WebClient webClient ) throws IOException {
        writeCookiesToFile( webClient, COOKIE_FILE );
    }

    /**
     * Serialises the current cookies to disk.
     * 
     * @throws IOException on serialisation error
     */
    public void writeCookiesToFile( WebClient webClient, String filename ) throws IOException {
        LOGGER.info( "writing cookies to file " + filename );
        ObjectOutput out = new ObjectOutputStream( new FileOutputStream( filename ) );
        out.writeObject( webClient.getCookieManager().getCookies() );
        out.close();
    }
    
    /**
     * Serialises the current cookies to disk.
     * 
     * @throws IOException on serialisation error
     */
    public void writeCookiesToFile( WebDriver webDriver, String filename ) throws IOException {
        LOGGER.info( "writing cookies to file " + filename );
        try (FileOutputStream fos = new FileOutputStream( filename )) {
            IOUtils.write( gson.toJson( webDriver.manage().getCookies() ), fos, StandardCharsets.UTF_8 );
        }
    }
    
    /**
     * Writes the given object to disk.
     * 
     * @param object object to serialize
     * @param filename name of file to write to
     * @throws IOException on serialisation error
     */
    public void serializeObjectToFile( Serializable object, String filename ) throws IOException {
        LOGGER.info( "writing object to disk " + filename );
        FileOutputStream fout = new FileOutputStream( filename );
        ObjectOutputStream oos = new ObjectOutputStream( fout );
        oos.writeObject( object );
        oos.close();
        fout.close();
    }

    /**
     * Loads the page that was saved to disk earlier.
     * 
     * @param fileName name of serialised page to load
     * @return the loaded page
     * @throws IOException on deserialisation error or if file does not exist
     */
    public HtmlPage loadPageFromDisk( String fileName ) throws IOException {
        LOGGER.info( "loading page from disk " + fileName );
        ObjectInputStream ois = new ObjectInputStream( new FileInputStream( fileName ) );
        try {
            return (HtmlPage) ois.readObject();
        }
        catch ( ClassNotFoundException e ) {
            throw new IOException( "Unable to read HtmlPage", e );
        }
        finally {
            ois.close();
        }
    }

}
