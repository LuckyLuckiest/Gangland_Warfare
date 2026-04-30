package org.luckyraven.gangland.inventory.filter;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs a {@link SearchFilter} against an arbitrary collection using a {@link FilterAdapter} for field projection.
 * Single reusable pipeline so every domain (gangs, members, shop items, auctions) shares the same filter semantics.
 */
public final class FilterApplier {

	public <T> List<T> apply(Collection<T> items, SearchFilter filter, FilterAdapter<T> adapter) {
		if (items == null || items.isEmpty()) return List.of();
		if (filter == null) return List.copyOf(items);

		Stream<T> stream = items.stream();

		for (Map.Entry<FilterField, FilterValue> entry : filter.values().entrySet()) {
			FilterField field = entry.getKey();
			FilterValue value = entry.getValue();
			stream = stream.filter(item -> matches(adapter.project(item, field), value));
		}

		SortDescriptor sort = filter.sort();
		if (sort != null) {
			stream = stream.sorted(comparator(adapter, sort));
		}

		return stream.collect(Collectors.toList());
	}

	private boolean matches(Object projected, FilterValue value) {
		if (value == null) return true;
		return switch (value) {
			case FilterValue.TextValue t -> projected != null
			                                && projected.toString().toLowerCase(Locale.ROOT)
			                                            .contains(t.value().toLowerCase(Locale.ROOT));
			case FilterValue.EnumValue e -> projected != null
			                                && projected.toString().equalsIgnoreCase(e.value());
			case FilterValue.RangeValue r -> projected instanceof Number n
			                                 && inRange(n.doubleValue(), r.min(), r.max());
			case FilterValue.DateValue d -> projected instanceof LocalDate date
			                                && (d.from() == null || !date.isBefore(d.from()))
			                                && (d.to() == null || !date.isAfter(d.to()));
			case FilterValue.BooleanValue b -> projected instanceof Boolean boxed && boxed == b.value();
		};
	}

	private boolean inRange(double value, Number min, Number max) {
		if (min != null && value < min.doubleValue()) return false;
		return max == null || !(value > max.doubleValue());
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private <T> Comparator<T> comparator(FilterAdapter<T> adapter, SortDescriptor sort) {
		Comparator<Object> nullSafe = Comparator.nullsLast((a, b) -> {
			if (a instanceof Comparable ca && b.getClass().isAssignableFrom(a.getClass())) {
				return ca.compareTo(b);
			}
			return a.toString().compareToIgnoreCase(b.toString());
		});

		Comparator<T> comparator = Comparator.comparing(item -> adapter.project(item, sort.field()), nullSafe);
		if (sort.direction() == SortDescriptor.Direction.DESC) comparator = comparator.reversed();
		return comparator;
	}

}
