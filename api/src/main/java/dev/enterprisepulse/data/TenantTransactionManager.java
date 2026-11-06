package dev.enterprisepulse.data;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/** Applies the tenant settings to the connection as soon as a transaction starts. */
public class TenantTransactionManager extends JpaTransactionManager {
    public TenantTransactionManager(EntityManagerFactory emf) {
        super(emf);
        Tenancy.bind(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        Tenancy.reapply();
    }
}
