package org.luckyraven.gangland.inventory.filter;

/**
 * Per-entity projection that extracts the comparable/matchable value for a given {@link FilterField}. One adapter per
 * entity type — e.g. {@code GangFilterAdapter}, {@code MemberFilterAdapter}. Return {@code null} for fields that do not
 * apply to this entity (the applier treats nulls as non-matches for predicates and sorts them last).
 */
@FunctionalInterface
public interface FilterAdapter<T> {

	Object project(T item, FilterField field);

}
