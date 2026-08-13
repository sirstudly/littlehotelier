package com.macbackpackers.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.jobs.AbstractJob;
import com.macbackpackers.jobs.CalculateEdinburghVisitorLevyForBookingJob;
import com.macbackpackers.jobs.CloudbedsAllocationScraperWorkerJob;
import com.macbackpackers.jobs.CreateAllocationScraperReportsJob;
import com.macbackpackers.jobs.HousekeepingJob;
import com.macbackpackers.jobs.JobPriorities;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Unit tests for the O(1) claim path in {@link WordPressDAOImpl#getNextJobToProcess()}.
 */
public class WordPressDAOGetNextJobToProcessTest {

    private WordPressDAOImpl dao;
    private EntityManager em;
    private Query abortQuery;
    private Query selectQuery;

    @BeforeEach
    public void setUp() throws Exception {
        dao = new WordPressDAOImpl();
        em = mock( EntityManager.class );
        abortQuery = mock( Query.class );
        selectQuery = mock( Query.class );

        setField( dao, "em", em );
        setField( dao, "processorId", "test-processor" );

        when( em.createNativeQuery( contains( "SET `status` = 'aborted'" ) ) ).thenReturn( abortQuery );
        when( abortQuery.setParameter( eq( "now" ), any( Timestamp.class ) ) ).thenReturn( abortQuery );
        when( abortQuery.executeUpdate() ).thenReturn( 0 );

        when( em.createNativeQuery( contains( "ORDER BY" ) ) ).thenReturn( selectQuery );
        when( selectQuery.setParameter( eq( "processedBy" ), anyString() ) ).thenReturn( selectQuery );
        when( selectQuery.setMaxResults( 1 ) ).thenReturn( selectQuery );
    }

    @Test
    public void prioritySqlCaseMatchesJavaPriorityOverrides() {
        String sqlCase = JobPriorities.sqlCaseExpression( "j.`classname`" );
        assertTrue( sqlCase.contains( CalculateEdinburghVisitorLevyForBookingJob.class.getName() ) );
        assertTrue( sqlCase.contains( CloudbedsAllocationScraperWorkerJob.class.getName() ) );
        assertTrue( sqlCase.contains( CreateAllocationScraperReportsJob.class.getName() ) );
        assertEquals( JobPriorities.forClass( CalculateEdinburghVisitorLevyForBookingJob.class ),
                new CalculateEdinburghVisitorLevyForBookingJob().getPriority() );
        assertEquals( JobPriorities.forClass( CloudbedsAllocationScraperWorkerJob.class ),
                new CloudbedsAllocationScraperWorkerJob().getPriority() );
        assertEquals( JobPriorities.forClass( CreateAllocationScraperReportsJob.class ),
                new CreateAllocationScraperReportsJob().getPriority() );
        assertEquals( -1, new CalculateEdinburghVisitorLevyForBookingJob().getPriority() );
        assertEquals( 99, new CloudbedsAllocationScraperWorkerJob().getPriority() );
        assertEquals( 99, new CreateAllocationScraperReportsJob().getPriority() );
        assertEquals( 0, new HousekeepingJob().getPriority() );
        assertTrue( sqlCase.contains( "THEN -1" ) );
        assertTrue( sqlCase.contains( "THEN 99" ) );
    }

    @Test
    public void returnsNullWhenNoEligibleJobs() {
        when( selectQuery.getResultList() ).thenReturn( Collections.emptyList() );

        assertNull( dao.getNextJobToProcess() );

        verify( abortQuery ).executeUpdate();
        verify( selectQuery ).setMaxResults( 1 );
        verify( em, never() ).find( eq( AbstractJob.class ), anyInt() );
    }

    @Test
    public void claimsSingleEligibleJobWithoutLoadingFullQueue() {
        when( selectQuery.getResultList() ).thenReturn( Arrays.asList( 42 ) );
        HousekeepingJob job = new HousekeepingJob();
        job.setId( 42 );
        job.setStatus( JobStatus.submitted );
        when( em.find( AbstractJob.class, 42 ) ).thenReturn( job );

        AbstractJob claimed = dao.getNextJobToProcess();

        assertNotNull( claimed );
        assertEquals( 42, claimed.getId() );
        assertEquals( JobStatus.processing, claimed.getStatus() );
        assertNotNull( claimed.getProcessedBy() );
        assertTrue( claimed.getProcessedBy().startsWith( "test-processor-" ) );
        assertNotNull( claimed.getJobStartDate() );

        // Abort pass + one SELECT ... LIMIT 1 — never hydrates the full submitted set
        verify( abortQuery, times( 1 ) ).executeUpdate();
        verify( selectQuery, times( 1 ) ).getResultList();
        verify( em, times( 1 ) ).find( AbstractJob.class, 42 );
    }

    @Test
    public void abortPassRunsBeforeSelectingNextJob() {
        when( abortQuery.executeUpdate() ).thenReturn( 3 );
        when( selectQuery.getResultList() ).thenReturn( Collections.emptyList() );

        assertNull( dao.getNextJobToProcess() );

        InOrder inOrder = inOrder( abortQuery, selectQuery );
        inOrder.verify( abortQuery ).executeUpdate();
        inOrder.verify( selectQuery ).getResultList();
    }

    @Test
    public void selectQueryIncludesDependencyAndPriorityClauses() {
        when( selectQuery.getResultList() ).thenReturn( Collections.emptyList() );

        dao.getNextJobToProcess();

        // Abort + select both reference the dependency table
        verify( em, times( 2 ) ).createNativeQuery( contains( "wp_lh_job_dependency" ) );
        verify( em ).createNativeQuery( contains( "CalculateEdinburghVisitorLevyForBookingJob" ) );
        verify( em ).createNativeQuery( contains( "NOT EXISTS" ) );
        verify( em ).createNativeQuery( contains( "SET `status` = 'aborted'" ) );
        verify( em ).createNativeQuery( contains( "AS doomed" ) );
    }

    private static void setField( Object target, String name, Object value ) throws Exception {
        Field field = target.getClass().getDeclaredField( name );
        field.setAccessible( true );
        field.set( target, value );
    }
}
