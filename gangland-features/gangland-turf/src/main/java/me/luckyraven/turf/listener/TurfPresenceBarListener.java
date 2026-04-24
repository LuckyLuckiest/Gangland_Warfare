package me.luckyraven.turf.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.events.TurfCapturedEvent;
import me.luckyraven.turf.events.TurfEnterEvent;
import me.luckyraven.turf.events.TurfExitEvent;
import me.luckyraven.turf.manager.TurfManager;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent "you are inside a turf" BossBar that appears the moment a player enters any turf and disappears the
 * instant they leave. Colour is picked per-viewer based on their relationship to the owning gang, so one player
 * standing in a turf sees GREEN while a rival next to them sees RED — even though it's the same turf.
 *
 * <p>Colour map (chosen to stack readably with the CONTESTING bossbar from {@link TurfBossBarListener} — none of
 * these overlap with the capture-bar palette of red/green/white):
 * <ul>
 *   <li><b>GREEN</b> — turf is owned by the viewer's own gang (friendly territory).</li>
 *   <li><b>RED</b> — turf is owned by a rival gang (hostile territory).</li>
 *   <li><b>BLUE</b> — turf is unclaimed and the viewer has a gang (opportunity to capture).</li>
 *   <li><b>YELLOW</b> — turf is owned by some gang but the viewer has no gang (bystander).</li>
 *   <li><b>WHITE</b> — turf is unclaimed and the viewer has no gang.</li>
 * </ul>
 *
 * <p>Bars are refreshed on {@link TurfCapturedEvent} so every viewer currently standing in the captured turf sees
 * their colour flip the moment ownership changes.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfPresenceBarListener implements Listener {

	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final UserLookupContract  users;
	private final TurfMessageContract messages;

	/**
	 * viewer → (turfId, bar) so we can tear down the right bar on exit without scanning.
	 */
	private final Map<UUID, Entry> active = new HashMap<>();

	@EventHandler
	public void onEnter(TurfEnterEvent event) {
		showFor(event.getPlayer(), event.getTurf());
	}

	@EventHandler
	public void onExit(TurfExitEvent event) {
		hideFor(event.getPlayer());
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		int capturedId = event.getTurf().getId();
		// Recolour every viewer whose current presence bar is for the just-captured turf.
		for (Map.Entry<UUID, Entry> entry : new HashMap<>(active).entrySet()) {
			if (entry.getValue().turfId != capturedId) {
				continue;
			}
			Player viewer = Bukkit.getPlayer(entry.getKey());
			if (viewer == null) {
				continue;
			}
			hideFor(viewer);
			showFor(viewer, event.getTurf());
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		hideFor(event.getPlayer());
	}

	private void showFor(Player viewer, Turf turf) {
		hideFor(viewer); // Guard against stacked duplicates if enter fires while an old bar is still up.

		Integer ownerId = turf.getOwnerGangId();
		Gang    owner   = ownerId == null ? null : gangs.findById(ownerId);
		String  title;
		if (owner != null) {
			title = messages.format("TURF_PRESENCE_OWNED",
			                        "turf", turf.getDisplayName(),
			                        "gang", GangDisplayNameResolver.resolve(owner));
		} else {
			title = messages.format("TURF_PRESENCE_UNCLAIMED",
			                        "turf", turf.getDisplayName());
		}

		BarColor color = resolveColor(viewer, ownerId);
		BossBar  bar   = Bukkit.createBossBar(ChatUtil.color(title), color, BarStyle.SOLID);
		bar.setProgress(1.0);
		bar.addPlayer(viewer);

		active.put(viewer.getUniqueId(), new Entry(turf.getId(), bar));
	}

	private void hideFor(Player viewer) {
		Entry entry = active.remove(viewer.getUniqueId());
		if (entry != null) {
			entry.bar.removeAll();
		}
	}

	private BarColor resolveColor(Player viewer, Integer ownerId) {
		User<Player> user    = users.findByPlayer(viewer);
		boolean      hasGang = user != null && user.hasGang();
		if (ownerId == null) {
			return hasGang ? BarColor.BLUE : BarColor.WHITE;
		}
		if (hasGang && user.getGangId() == ownerId) {
			return BarColor.GREEN;
		}
		return hasGang ? BarColor.RED : BarColor.YELLOW;
	}

	private record Entry(int turfId, BossBar bar) {
	}
}
