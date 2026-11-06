package dev.enterprisepulse.resource;

import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/** Lets a module validate or enrich generic create / update / delete of one resource. */
public interface ResourceHook {
    String resource();

    /** Extra row restriction for list, lookup and find (rows that are not tenant-owned); null for none. */
    default Predicate scope(CriteriaBuilder cb, AbstractQuery<?> query, Root<?> root) {
        return null;
    }

    default void beforeSave(Object entity, boolean isNew) {}

    default void afterSave(Object entity, boolean isNew) {}

    default void beforeDelete(Object entity) {}
}
