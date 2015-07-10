
package com.macbackpackers.dao;

import java.util.Collection;
import java.util.List;

import javax.transaction.Transactional;

import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.macbackpackers.beans.NameValuePair;

@Repository
public class TestHarnessDAO {

    @Autowired
    private SessionFactory sessionFactory;

    @Transactional
    public void deleteAllTransactionalData() {
        sessionFactory.getCurrentSession().createSQLQuery( "DELETE FROM wp_lh_rpt_split_rooms" ).executeUpdate();
        sessionFactory.getCurrentSession().createSQLQuery( "DELETE FROM wp_lh_calendar" ).executeUpdate();
        sessionFactory.getCurrentSession().createSQLQuery( "DELETE FROM wp_lh_rpt_unpaid_deposit" ).executeUpdate();

        // job data
        sessionFactory.getCurrentSession().createSQLQuery( "DELETE FROM wp_lh_job_param" ).executeUpdate();
        sessionFactory.getCurrentSession().createSQLQuery( "DELETE FROM wp_lh_jobs" ).executeUpdate();
    }

    @Transactional
    public void runSQL( String sql ) {
        sessionFactory.getCurrentSession().createSQLQuery( sql ).executeUpdate();
    }

    @Transactional
    public void save( Object object ) {
        sessionFactory.getCurrentSession().save( object );
    }

    @Transactional
    public <T> List<T> list( String queryString, Class<T> ofType ) {
        return list( queryString, null, ofType );
    }

    @Transactional
    @SuppressWarnings( "unchecked" )
    public <T> List<T> list( String queryString, Collection<? extends NameValuePair> parameters, Class<T> ofType ) {
        Query q = sessionFactory.getCurrentSession().createQuery( queryString );
        for ( NameValuePair nvp : parameters ) {
            q.setParameter( nvp.getName(), nvp.getValue() );
        }
        return q.list();
    }
}
