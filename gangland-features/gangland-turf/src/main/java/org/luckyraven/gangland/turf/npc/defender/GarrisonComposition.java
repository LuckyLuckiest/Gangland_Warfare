package org.luckyraven.gangland.turf.npc.defender;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.npc.NpcRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The roles a turf's guards take, in spawn order ({@code Garrison.Composition}: {@code Commander@1}, {@code Shield@2},
 * ...). Guard number {@code n} (1-based, per contest) takes the n-th slot; past the last slot a guard is plain.
 */
public record GarrisonComposition(List<String> slots) {

	/** No composition: every guard is plain. */
	public static final GarrisonComposition NONE = new GarrisonComposition(List.of());

	/** The shipped composition, for a file that has no {@code Garrison.Composition}. */
	public static final GarrisonComposition DEFAULT = parse(List.of("Commander@1", "Shield@2", "Marksman@2"), w -> { });

	/** Reads {@code Name} / {@code Name@count} entries; a bad count reports once and counts 1. */
	public static GarrisonComposition parse(@Nullable List<String> raw, Consumer<String> warn) {
		if (raw == null || raw.isEmpty()) return NONE;
		List<String> slots = new ArrayList<>();
		for (String entry : raw) {
			if (entry == null || entry.isBlank()) continue;
			String name  = entry.trim();
			int    count = 1;
			int    at    = name.indexOf('@');
			if (at >= 0) {
				String number = name.substring(at + 1).trim();
				name = name.substring(0, at).trim();
				try {
					count = Math.max(1, Integer.parseInt(number));
				} catch (NumberFormatException e) {
					warn.accept("Garrison.Composition entry '" + entry + "': '" + number + "' is not a count, 1 used");
				}
			}
			if (name.isEmpty()) continue;
			for (int i = 0; i < count; i++) {
				slots.add(name);
			}
		}
		return slots.isEmpty() ? NONE : new GarrisonComposition(List.copyOf(slots));
	}

	/** The role name guard {@code number} (1-based) takes, {@code null} for a plain guard. */
	public @Nullable String roleNameFor(int number) {
		return number >= 1 && number <= slots.size() ? slots.get(number - 1) : null;
	}

	/**
	 * The role guard {@code number} takes out of {@code known} (the guard type's roles, by name), {@code null} for a
	 * plain guard. A name {@code known} lacks is reported once through {@code warn} (tracked in {@code warned}).
	 */
	public @Nullable NpcRole assign(int number, Map<String, NpcRole> known, Set<String> warned,
	                                Consumer<String> warn) {
		String name = roleNameFor(number);
		if (name == null) return null;
		NpcRole role = known.get(name);
		if (role == null && warned.add(name)) {
			warn.accept("Garrison.Composition names role '" + name + "' that the guard type does not define, plain guards used");
		}
		return role;
	}
}
