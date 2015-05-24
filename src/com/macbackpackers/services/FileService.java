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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

@Service
public class FileService {
    
    private final Logger LOGGER = LogManager.getLogger(getClass());
    
    private static final String COOKIE_FILE = "cookie.file";
   
    /**
     * Attempts to acquire an exclusive lock on the given file.
     * Creates the file first if it doesn't exist. This method will *not*
     * block if it cannot acquire the lock.
     * 
     * @param file the lock file
     * @return the file lock (or null if lock could not be acquired)
     * @throws IOException if file could not be created
     */
    public FileLock lockFile( File file ) throws IOException {
        
        FileLock lock = null;

        LOGGER.info( "attempting file lock on " + file.getName() );
        
        if( false == file.exists() ) {
            file.createNewFile();
        }
        
        try {
           
            // Get a file channel for the file
            //File file = new File("filename");
            FileChannel channel = new RandomAccessFile(file, "rw").getChannel();

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
            } catch (OverlappingFileLockException e) {
                // File is already locked in this thread or virtual machine
                LOGGER.info( "File is already locked in this thread or virtual machine" );
            } 
            
            if( lock == null ) {
                LOGGER.info( "could not acquire file lock" );
            } else {
                LOGGER.info( "file lock acquired" );
            }

            // Release the lock
//            if( lock != null ) {
//                lock.release();
//            }
//            
//            // Close the file
//            channel.close();
            
            
        } catch (IOException e) {

            LOGGER.error(e);

        }
        return lock;
    }
    
    /**
     * Loads cookies written from the previous session if found.
     * 
     * @throws IOException on read error
     */
    public void loadCookiesFromFile( WebClient webClient ) throws IOException {
        
        File file = new File( COOKIE_FILE );
        LOGGER.info( "loading cookies from file " + COOKIE_FILE );
        if( file.exists() ) {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
            try {
                @SuppressWarnings("unchecked")
                Set<Cookie> cookies = (Set<Cookie>) in.readObject();
        
                for (Iterator<Cookie> i = cookies.iterator(); i.hasNext(); ) {
                    webClient.getCookieManager().addCookie(i.next());
                }
            } catch ( ClassNotFoundException e ) {
                throw new IOException( "Unable to read cookie!", e );
            }
            finally {
                in.close();
            }
        }
    }

    /**
     * Serialises the current cookies to disk.
     * 
     * @throws IOException on serialisation error
     */
    public void writeCookiesToFile( WebClient webClient ) throws IOException {
        LOGGER.info( "writing cookies to file " + COOKIE_FILE );
        ObjectOutput out = new ObjectOutputStream(new FileOutputStream( COOKIE_FILE ));
        out.writeObject( webClient.getCookieManager().getCookies() );
        out.close();
    }
    
    /**
     * Serialises the given page to disk so it can be loaded in later.
     * 
     * @param page page to serialise
     * @param filename name of file to write to
     */
    public void serialisePageToDisk( HtmlPage page, String filename ) {
        try {
            LOGGER.info( "serialising page to disk " + filename );
            FileOutputStream fout = new FileOutputStream( filename );
            ObjectOutputStream oos = new ObjectOutputStream( fout );
            oos.writeObject( page );
            oos.close();

        } catch ( FileNotFoundException e ) {
            LOGGER.error( e );
        } catch ( IOException e ) {
            LOGGER.error( e );
        }
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
        } catch ( ClassNotFoundException e ) {
            throw new IOException( "Unable to read HtmlPage", e );
        } finally {
            ois.close();
        }
    }

}
