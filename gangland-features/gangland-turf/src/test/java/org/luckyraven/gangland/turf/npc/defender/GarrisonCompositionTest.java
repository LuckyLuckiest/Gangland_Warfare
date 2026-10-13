package org.luckyraven.gangland.turf.npc.defender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.npc.NpcFanPlacement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("GarrisonComposition - the roles a turf's guards take, in spawn order")
class GarrisonCompositionTest {

	private static NpcRole role(String name) {
		return new NpcRole(name, name, NpcFanPlacement.ANY, null, null, 1.0, 0, null, 1.0, 0, null, 0, 0, false,
		                   false);
	}

	private final Map<String, NpcRole> known = Map.of("Commander", role("Commander"), "Shield", role("Shield"),
	                                                  "Marksman", role("Marksman"));

	@Test
	@DisplayName("Commander@1, Shield@2, Marksman@2: guards 1..5 get those roles, guard 6 is plain")
	void rolesAssignedInOrder() {
		GarrisonComposition composition = GarrisonComposition.parse(List.of("Commander@1", "Shield@2", "Marksman@2"),
		                                                            w -> { });

		List<String> names = new ArrayList<>();
		for (int number = 1; number <= 6; number++) {
			names.add(composition.roleNameFor(number));
		}
		assertEquals(Arrays.asList("Commander", "Shield", "Shield", "Marksman", "Marksman", null), names);
		assertSame(known.get("Shield"), composition.assign(2, known, new HashSet<>(), w -> { }));
		assertNull(composition.assign(6, known, new HashSet<>(), w -> { }));
	}

	@Test
	@DisplayName("an unknown role name is reported once and that guard is plain; the next slot still applies")
	void unknownRole_plainGuard() {
		GarrisonComposition composition = GarrisonComposition.parse(List.of("Sniper@2", "Shield"), w -> { });
		List<String>        warnings    = new ArrayList<>();
		HashSet<String>     warned      = new HashSet<>();

		assertNull(composition.assign(1, known, warned, warnings::add));
		assertNull(composition.assign(2, known, warned, warnings::add));
		assertSame(known.get("Shield"), composition.assign(3, known, warned, warnings::add));

		assertEquals(1, warnings.size(), warnings.toString());
	}

	@Test
	@DisplayName("a bad count is reported and counts 1; a missing list means no roles")
	void badCount_andEmpty() {
		List<String>        warnings    = new ArrayList<>();
		GarrisonComposition composition = GarrisonComposition.parse(List.of("Shield@x", "Marksman"), warnings::add);

		assertEquals("Shield", composition.roleNameFor(1));
		assertEquals("Marksman", composition.roleNameFor(2));
		assertNull(composition.roleNameFor(3));
		assertEquals(1, warnings.size());
		assertNull(GarrisonComposition.parse(null, w -> { }).roleNameFor(1));
	}
}
