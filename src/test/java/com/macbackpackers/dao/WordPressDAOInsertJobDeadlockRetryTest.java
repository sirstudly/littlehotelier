package com.macbackpackers.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.jobs.HousekeepingJob;

import jakarta.persistence.EntityManager;

/**
 * Verifies {@link WordPressDAOImpl#insertJob} retries MySQL deadlocks.
 */
public class WordPressDAOInsertJobDeadlockRetryTest {

    private WordPressDAOImpl dao;
    private EntityManager em;
    private PlatformTransactionManager txManager;

    @BeforeEach
    public void setUp() throws Exception {
        dao = new WordPressDAOImpl();
        em = mock( EntityManager.class );
        txManager = mock( PlatformTransactionManager.class );
        setField( dao, "em", em );
        setField( dao, "transactionManager", txManager );

        // Minimal TX lifecycle for TransactionTemplate
        doAnswer( inv -> new SimpleTransactionStatus() ).when( txManager ).getTransaction( any( TransactionDefinition.class ) );
    }

    @Test
    public void retriesPersistOnDeadlockThenSucceeds() {
        AtomicInteger persists = new AtomicInteger();
        doAnswer( inv -> {
            if ( persists.incrementAndGet() == 1 ) {
                throw new CannotAcquireLockException( "Deadlock found when trying to get lock" );
            }
            return null;
        } ).when( em ).persist( any( Job.class ) );

        HousekeepingJob job = new HousekeepingJob();
        job.setStatus( JobStatus.submitted );
        job.setId( 0 );

        // After persist, Hibernate would assign id; simulate for return value
        doAnswer( inv -> {
            if ( job.getId() == 0 ) {
                job.setId( 99 );
            }
            return null;
        } ).when( em ).flush();

        int id = dao.insertJob( job );
        assertEquals( 99, id );
        verify( em, times( 2 ) ).persist( job );
        verify( txManager, times( 1 ) ).commit( any( TransactionStatus.class ) );
    }

    @Test
    public void givesUpAfterThreeDeadlocks() {
        doThrow( new CannotAcquireLockException( "Deadlock found when trying to get lock" ) )
                .when( em ).persist( any( Job.class ) );

        HousekeepingJob job = new HousekeepingJob();
        job.setStatus( JobStatus.submitted );

        assertThrows( CannotAcquireLockException.class, () -> dao.insertJob( job ) );
        verify( em, times( 3 ) ).persist( job );
    }

    private static void setField( Object target, String name, Object value ) throws Exception {
        Field field = target.getClass().getDeclaredField( name );
        field.setAccessible( true );
        field.set( target, value );
    }
}
