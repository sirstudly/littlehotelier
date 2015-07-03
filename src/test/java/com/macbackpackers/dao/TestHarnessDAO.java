
package com.macbackpackers.dao;

import javax.transaction.Transactional;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

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

}
