package me.luckyraven.inventory.filter;

import java.time.LocalDate;

/**
 * Typed value carried inside a {@link SearchFilter} for a given {@link FilterField}. Sealed so {@link FilterApplier}
 * can exhaustively switch over the variants.
 */
public sealed interface FilterValue {

	record TextValue(String value) implements FilterValue { }

	record EnumValue(String value) implements FilterValue { }

	record RangeValue(Number min, Number max) implements FilterValue { }

	record DateValue(LocalDate from, LocalDate to) implements FilterValue { }

	record BooleanValue(boolean value) implements FilterValue { }

}
