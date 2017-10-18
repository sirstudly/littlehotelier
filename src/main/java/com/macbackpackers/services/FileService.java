
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
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

@Service
public class FileService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private static final String COOKIE_FILE = "cookie.file";

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
        LOGGER.info( "loading cookies from file " + filename );
        if ( file.exists() ) {
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
    }

    /**
     * Taken from Chrome (Under Developer Tools, Application, Cookies). I've just wrote the
     * cookie.file after loading these and copied it manually to the server until I sort out how to
     * login properly in LH.
     * 
     * @param webClient the web client
     */
    public void loadCookiesForHighStreet( WebClient webClient ) {
        webClient.getCookieManager().addCookie( new Cookie( "app.littlehotelier.com", "_littlehotelier_session",
                "VzlPZTNQdjhOZTFnd2orU2VpQXBvMGNSV1ZWM3N0VXN5VVl0Y21RNUhVQmx1bXhaODc0VkxZdjZodmpkaktxMUdYU0xYOTlZbVV6Q0ZqcitDbVQ1eXVzRVRRZktuMnhrcm9LcDFmWmJKY3ExZE1OV0lwZ3IyNTlEOE9nTDdaR2V4VnpVUjFUUnNTQXIxcVp0V0cvamRkYlQ3ZGdNWGU2SXprZnRzZmlRcUdnRlZBaGl2SVhnaFp1MGdpdE5UUlJmV2U5WEJrMEMvTVhBV21FWEhZUmIyeGZ4U1k3eGIvQ2xiY3JnY0hLZFRMMkV6SzFvVXh6QVdPMEk1aFhqaWRSY3BjWllwR0g5UGxPSWJWUzMvOWxFbTYrTXA3SlhSNUt1blJzWVBEc1Bremc0MWNjWVMzS2pMTVpBeUJ3YlVaMHc4S3pHTFFOZE9kNEhEWnpmUDgyTmdmcG50QnlrMEoybjJMVVduRDRla2FkVjRnUVgxeGo4aTAyeVA5ZkxYK2I3L1lyUmJEcFJheXlBbUNPZDdNcHJ4NWJXU0tZek9KV3RIeHBtRnhsRTBKa0xPSFRQZnNnMVhST1hJRXJUTTZtK2FUY09tbG01eHZORytTTzAvSVVTUndEY0xpZWRxT0xDNGpTZWF6dEhPRVlYb3gvU1RtRUJscjJ5a093TUtNTk8tLXpLQUx3M2dsMElXODVmSkRRbkJEUlE9PQ%3D%3D--ff9ae910ea02d77a91a0be3f057eed65a07178ab" ) );
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
