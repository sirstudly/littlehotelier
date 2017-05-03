
package com.macbackpackers.dao;

import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.macbackpackers.beans.NameValuePair;

@Repository
public class TestHarnessDAO {

    @PersistenceContext
    private EntityManager em;
    
    @Transactional
    public void deleteAllTransactionalData() {
        em.createNativeQuery( "DELETE FROM wp_lh_rpt_split_rooms" ).executeUpdate();
        em.createNativeQuery( "DELETE FROM wp_lh_calendar" ).executeUpdate();
        em.createNativeQuery( "DELETE FROM wp_lh_rpt_unpaid_deposit" ).executeUpdate();

        // job data
        em.createNativeQuery( "DELETE FROM wp_lh_job_param" ).executeUpdate();
        em.createNativeQuery( "DELETE FROM wp_lh_jobs" ).executeUpdate();
    }

    @Transactional
    public void runSQL( String sql ) {
        em.createNativeQuery( sql ).executeUpdate();
    }

    @Transactional
    public void save( Object object ) {
        em.merge( object );
    }

    @Transactional
    public <T> List<T> list( String queryString, Class<T> ofType ) {
        return list( queryString, null, ofType );
    }

    @Transactional
    public <T> List<T> list( String queryString, Collection<? extends NameValuePair> parameters, Class<T> ofType ) {
        TypedQuery<T> q = em.createQuery( queryString, ofType );
        for ( NameValuePair nvp : parameters ) {
            q.setParameter( nvp.getName(), nvp.getValue() );
        }
        return q.getResultList();
    }
}
