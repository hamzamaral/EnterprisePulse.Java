package dev.enterprisepulse.data;

import dev.enterprisepulse.common.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Small JPQL helper over the transaction-bound entity manager (positional parameters ?1, ?2 ...). */
@Component
public class Db {
    @PersistenceContext
    private EntityManager em;

    public EntityManager em() {
        return em;
    }

    public <T> TypedQuery<T> query(String jpql, Class<T> type, Object... params) {
        TypedQuery<T> q = em.createQuery(jpql, type);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        return q;
    }

    public <T> List<T> list(String jpql, Class<T> type, Object... params) {
        return query(jpql, type, params).getResultList();
    }

    public <T> Optional<T> first(String jpql, Class<T> type, Object... params) {
        return query(jpql, type, params).setMaxResults(1).getResultList().stream().findFirst();
    }

    public <T> T one(String jpql, Class<T> type, Object... params) {
        return first(jpql, type, params).orElseThrow(AppException::notFound);
    }

    public long count(String jpql, Object... params) {
        return query(jpql, Long.class, params).getSingleResult();
    }

    public boolean exists(String jpql, Object... params) {
        return !query(jpql, Object.class, params).setMaxResults(1).getResultList().isEmpty();
    }

    /** Bulk JPQL update/delete. */
    public int execute(String jpql, Object... params) {
        Query q = em.createQuery(jpql);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        return q.executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> nativeRows(String sql, Object... params) {
        Query q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        return q.getResultList();
    }

    public int nativeExecute(String sql, Object... params) {
        Query q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        return q.executeUpdate();
    }

    public <T> T find(Class<T> type, Object id) {
        return em.find(type, id);
    }

    public <T> T get(Class<T> type, Object id) {
        T found = em.find(type, id);
        if (found == null) throw AppException.notFound();
        return found;
    }

    public <T> T add(T entity) {
        em.persist(entity);
        return entity;
    }

    @SafeVarargs
    public final <T> void addAll(T... entities) {
        for (T e : entities) em.persist(e);
    }

    public <T> void addAll(Iterable<T> entities) {
        for (T e : entities) em.persist(e);
    }

    /** Soft-deletes rows that support it, otherwise removes them. */
    public void remove(Object entity) {
        if (entity instanceof SoftDelete soft) soft.setDeletedAt(Instant.now());
        else em.remove(entity);
    }

    public void flush() {
        em.flush();
    }

    public void clear() {
        em.clear();
    }
}
