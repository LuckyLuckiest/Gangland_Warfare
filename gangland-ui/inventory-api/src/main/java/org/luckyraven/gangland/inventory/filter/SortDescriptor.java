package org.luckyraven.gangland.inventory.filter;

import java.util.List;
import java.util.Objects;

public record SortDescriptor(FilterField field, Direction direction) {

	public static SortDescriptor asc(FilterField field) {
		return new SortDescriptor(field, Direction.ASC);
	}

	public static SortDescriptor desc(FilterField field) {
		return new SortDescriptor(field, Direction.DESC);
	}

	public SortDescriptor cycle(List<SortDescriptor> cycle) {
		if (cycle == null || cycle.isEmpty()) return this;
		int index = -1;
		for (int i = 0; i < cycle.size(); i++) {
			SortDescriptor candidate = cycle.get(i);
			if (Objects.equals(candidate.field().id(), field.id()) && candidate.direction() == direction) {
				index = i;
				break;
			}
		}
		return cycle.get((index + 1) % cycle.size());
	}

	public String key() {
		return field.id() + ":" + direction.name();
	}

	public enum Direction {

		ASC,
		DESC;

		public Direction flip() {
			return this == ASC ? DESC : ASC;
		}

	}

}
