package org.luckyraven.gangland.gang.combat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Who damaged whom recently (kill and assist credit; the combat tag reads the same hits). Filled by
 * {@code DamageLedgerListener} at HIGH priority, after the friendly-fire cancel and before the lethal-hit intercept.
 * ponytail: a hit is forgotten after {@link #RETENTION_MILLIS}; the credit window itself is the reader's choice.
 */
public class DamageLedger {

	/** Longest a hit stays on the books; a death more than this long after the last hit credits nobody. */
	static final long RETENTION_MILLIS = 2 * 60_000L;

	private record Hit(UUID attacker, long at) { }

	private final Map<UUID, Deque<Hit>> hits = new ConcurrentHashMap<>();
	private final LongSupplier          clock;

	public DamageLedger() {
		this(System::currentTimeMillis);
	}

	public DamageLedger(LongSupplier clock) {
		this.clock = clock;
	}

	public void record(UUID attacker, UUID victim, double damage, long atMillis) {
		if (attacker == null || victim == null || damage <= 0D) return;

		Deque<Hit> deque = hits.computeIfAbsent(victim, key -> new ArrayDeque<>());
		synchronized (deque) {
			prune(deque);
			deque.addLast(new Hit(attacker, atMillis));
		}
	}

	/** The player who hurt {@code victim} last, if recently. */
	public Optional<UUID> lastDamager(UUID victim) {
		Deque<Hit> deque = hits.get(victim);
		if (deque == null) return Optional.empty();

		synchronized (deque) {
			prune(deque);
			return deque.isEmpty() ? Optional.empty() : Optional.of(deque.peekLast().attacker());
		}
	}

	/** Players other than the last damager who hurt {@code victim} within {@code window}, newest first. */
	public List<UUID> assistants(UUID victim, Duration window) {
		Deque<Hit> deque = hits.get(victim);
		if (deque == null) return List.of();

		synchronized (deque) {
			prune(deque);
			if (deque.isEmpty()) return List.of();

			UUID      last   = deque.peekLast().attacker();
			long      oldest = clock.getAsLong() - window.toMillis();
			Set<UUID> found  = new LinkedHashSet<>();
			for (Iterator<Hit> it = deque.descendingIterator(); it.hasNext(); ) {
				Hit hit = it.next();
				if (hit.at() >= oldest && !hit.attacker().equals(last)) found.add(hit.attacker());
			}
			return new ArrayList<>(found);
		}
	}

	/** Forgets everything recorded against {@code victim} (after a death has been credited). */
	public void clear(UUID victim) {
		hits.remove(victim);
	}

	private void prune(Deque<Hit> deque) {
		long oldest = clock.getAsLong() - RETENTION_MILLIS;
		while (!deque.isEmpty() && deque.peekFirst().at() < oldest) deque.pollFirst();
	}
}
