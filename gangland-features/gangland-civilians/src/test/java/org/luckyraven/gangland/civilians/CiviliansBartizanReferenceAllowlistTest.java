package org.luckyraven.gangland.civilians;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BartizanReferenceScan;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * W25 ruling (WS7 G5b fix round 1): {@link BartizanReferenceScan} catches a Bartizan reference ANYWHERE in a
 * compiled class (method bodies, fields, locals) - not just in a scanned annotation's own signature the way
 * {@link CiviliansBartizanBlindScanTest} does. Review C2 (a stray {@code BartizanApi.class} literal reached before
 * any availability guard) slipped past the signature-only scan entirely; this allowlist test is the backstop.
 */
@DisplayName("gangland-civilians — Bartizan constant-pool reference allowlist (WS7 G5b fix round 1, W25)")
class CiviliansBartizanReferenceAllowlistTest {

	@Test
	@DisplayName("only the four audited classes reference Bartizan anywhere in their compiled bytecode")
	void onlyAuditedClassesReferenceBartizanAnywhere() throws IOException {
		Set<String> found = BartizanReferenceScan.findBartizanReferencingClasses(Path.of("target/classes"));
		Set<String> allowed = Set.of(
				"org.luckyraven.gangland.civilians.listener.gang.GangAllyWeaponImpactListener",
				"org.luckyraven.gangland.civilians.CombatEligibilityConfig",
				"org.luckyraven.gangland.civilians.npc.combat.GanglandCombatEligibility",
				"org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons"
		);
		assertEquals(allowed, found);
	}
}
