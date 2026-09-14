package com.macbackpackers.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import com.macbackpackers.dao.WordPressDAOImpl;

public class AllocationListBulkInsertTest {

    @Test
    public void bulkInsertStatementPlaceholderCountMatchesBatchSize() {
        int batchSize = WordPressDAOImpl.ALLOCATION_INSERT_BATCH_SIZE;
        AllocationList batch = new AllocationList();
        for ( int i = 0 ; i < batchSize ; i++ ) {
            batch.add( new Allocation() );
        }

        String sql = batch.getBulkInsertStatement();
        int paramCount = StringUtils.countMatches( Allocation.getColumnNames(), ',' ) + 1;
        assertEquals( batchSize, StringUtils.countMatches( sql, "(" + StringUtils.repeat( "?", ",", paramCount ) + ")" ) );
        assertEquals( batchSize * paramCount, StringUtils.countMatches( sql, "?" ) );
        assertFalse( sql.contains( "??" ) );
    }

    @Test
    public void sublistProducesCorrectSizedInsert() {
        AllocationList all = new AllocationList();
        for ( int i = 0 ; i < WordPressDAOImpl.ALLOCATION_INSERT_BATCH_SIZE + 5 ; i++ ) {
            all.add( new Allocation() );
        }
        AllocationList first = new AllocationList( all.subList( 0, WordPressDAOImpl.ALLOCATION_INSERT_BATCH_SIZE ) );
        AllocationList last = new AllocationList( all.subList( WordPressDAOImpl.ALLOCATION_INSERT_BATCH_SIZE, all.size() ) );

        assertEquals( WordPressDAOImpl.ALLOCATION_INSERT_BATCH_SIZE,
                StringUtils.countMatches( first.getBulkInsertStatement(), "),(" ) + 1 );
        assertEquals( 5, StringUtils.countMatches( last.getBulkInsertStatement(), "),(" ) + 1 );
    }
}
