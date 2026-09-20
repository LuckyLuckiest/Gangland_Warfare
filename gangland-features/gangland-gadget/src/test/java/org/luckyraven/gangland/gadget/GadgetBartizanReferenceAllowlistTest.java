package org.luckyraven.gangland.gadget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BartizanReferenceScan;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WS7 G5 fix round 1 (ruling W25): {@link BartizanReferenceScan} catches a Bartizan reference ANYWHERE in a
 * compiled class's constant pool, not just in a scanned annotation's own signature the way
 * {@link org.luckyraven.gangland.core.testsupport.BartizanBlindScan} does — this is what would have caught review
 * findings C1/C2. The allowlist is the complete, explicitly audited set of gadget classes permitted to reference
 * Bartizan at all; a new one appearing here without a matching allowlist update means an unaudited Bartizan
 * reference leaked into an always-loaded class.
 */
@DisplayName("gangland-gadget — Bartizan constant-pool reference allowlist (WS7 G5 fix round 1, W25)")
class GadgetBartizanReferenceAllowlistTest {

	@Test
	@DisplayName("only the audited classes reference Bartizan anywhere in their compiled constant pool")
	void onlyAuditedClassesReferenceBartizanAnywhere() throws IOException {
		Set<String> found = BartizanReferenceScan.findBartizanReferencingClasses(Path.of("target/classes"));
		Set<String> allowed = Set.of(
				"org.luckyraven.gangland.gadget.listener.car.CarWeaponDamageListener",
				"org.luckyraven.gangland.gadget.listener.car.CarMeleeWeaponLookup",
				"org.luckyraven.gangland.gadget.jetpack.config.JetpackBartizanTraitBridge"
		);
		assertEquals(allowed, found);
	}
}
