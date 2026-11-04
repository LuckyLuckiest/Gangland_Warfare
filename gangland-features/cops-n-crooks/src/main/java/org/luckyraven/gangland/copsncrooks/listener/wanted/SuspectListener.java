package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFilter;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescription;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snapshots the suspect description on every crime and forgets it when the chase ends (0.20.0 lane disguise). A crime
 * the witness gate holds keeps the look it was done in, and the report that lands for it records that look (0.21.5); an
 * escape keeps the description for the post-escape search.
 */
@CustomLog
@ListenerHandler
public class SuspectListener implements Listener {

	private final SuspectDescriptions          descriptions;
	private final PoliceOutfit                 outfit;
	private final DisguiseTick                 tick;
	private final HeatLedger                   ledger;
	private final PostEscapeSearch             search;
	private final DisguiseFilter               filter;
	private final Map<UUID, Map<String, Held>> held            = new ConcurrentHashMap<>();
	private final Set<UUID>                    unhuntedAtCrime = ConcurrentHashMap.newKeySet();

	public SuspectListener(SuspectDescriptions descriptions, PoliceOutfit outfit, DisguiseTick tick, HeatLedger ledger,
	                       PostEscapeSearch search, DisguiseFilter filter) {
		this.descriptions = descriptions;
		this.outfit       = outfit;
		this.tick         = tick;
		this.ledger       = ledger;
		this.search       = search;
		this.filter       = filter;
	}

	/**
	 * Before any heat is committed (HeatListener and onCrime share MONITOR, in no set order): whether nobody hunted him
	 * when this crime began, so a blown outfit left from an earlier chase is stale.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onCrimeBegins(CrimeCommittedEvent event) {
		Player player = event.getPlayer();
		UUID   id     = player.getUniqueId();
		// set afresh on every crime: a gated crime's entry must not outlive it into a crime of a chase
		if (filter.hunted(player)) {
			unhuntedAtCrime.remove(id);
		} else {
			unhuntedAtCrime.add(id);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR) // no ignoreCancelled: the gate's cancel is the moment of the crime
	public void onCrime(CrimeCommittedEvent event) {
		Player player = event.getPlayer();
		UUID   id     = player.getUniqueId();
		if (event.isCancelled()) {
			// ponytail: one held look per crime id; two reports of one crime id pending at once share the newer look
			held.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
			    .put(event.getCrimeId(), new Held(descriptions.describe(player), outfit.worn(player)));
			return;
		}

		Map<String, Held> mine = held.get(id);
		Held              look = mine == null ? null : mine.remove(event.getCrimeId());
		if (look == null || !ledger.isReporting(id)) {
			look = new Held(descriptions.describe(player), outfit.worn(player));
		}

		descriptions.store(id, look.description());
		// a crime outside any chase: the blown flag belongs to a chase that ended unseen (a quit, stars cleared
		// offline) or to a crime that started none; this crime's own blow follows
		// ponytail: a chase started without a crime of his own (crew-share stars, admin-set stars) passes nothing
		// through here, so it keeps a stale blow; a WantedStartEvent hook ordered against HeatListener is the upgrade
		if (unhuntedAtCrime.remove(id)) {
			outfit.clear(id);
		}
		if (look.outfitWorn()) {
			outfit.blow(id);
		}

		log.debug("SUSPECT {} desc={}", player.getName(), descriptions.format(look.description()));
	}

	@EventHandler
	public void onChaseEnd(WantedEndEvent event) {
		// an escape keeps the look and the blown outfit for the post-escape search (0.21.5, row 844); the search giving
		// up clears them in DisguiseTick
		if (search.isEscape(event.getCause())) return;

		Player player = event.getPlayer();

		descriptions.clear(player.getUniqueId());
		outfit.clear(player.getUniqueId());
		tick.forget(player.getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();

		// a report deferred to the next join records the look at the join (D14); the blown outfit stays (D7)
		descriptions.clear(id);
		held.remove(id);
		unhuntedAtCrime.remove(id);
		tick.forget(id);
	}

	/** The look at a crime the witness gate held, per crime id: the report that lands for it records this look. */
	private record Held(SuspectDescription description, boolean outfitWorn) {}
}
