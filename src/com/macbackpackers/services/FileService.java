package com.macbackpackers.services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.log.Log;

public class FileService {
    
    private Logger logger = LogManager.getLogger(getClass());
   
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

        logger.info( "attempting file lock on " + file.getName() );
        
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
                logger.info( "File is already locked in this thread or virtual machine" );
            } 
            
            if( lock == null ) {
                logger.info( "could not acquire file lock" );
            } else {
                logger.info( "file lock acquired" );
            }

            // Release the lock
//            if( lock != null ) {
//                lock.release();
//            }
//            
//            // Close the file
//            channel.close();
            
            
        } catch (IOException e) {

            logger.error(e);

        }
        return lock;

    }
}
