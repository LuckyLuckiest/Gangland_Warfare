package org.luckyraven.gangland.gang.combat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who damaged whom recently (kill and assist credit). A state bean: the listener that fills it is lane W5-F's
 * {@code DamageLedgerListener}. Seam: empty until then.
 */
public class DamageLedger {

	public DamageLedger() {
	}

	public DamageLedger(java.util.function.LongSupplier clock) {
	}

	public void record(UUID attacker, UUID victim, double damage, long atMillis) {
		// W5-F
	}

	public Optional<UUID> lastDamager(UUID victim) {
		return Optional.empty();
	}

	public List<UUID> assistants(UUID victim, Duration window) {
		return List.of();
	}
}
