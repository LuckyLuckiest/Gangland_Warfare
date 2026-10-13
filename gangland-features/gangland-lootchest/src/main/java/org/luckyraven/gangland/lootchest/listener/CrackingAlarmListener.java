package org.luckyraven.gangland.lootchest.listener;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.lootchest.config.CrackingAlarmSettings;
import org.luckyraven.gangland.lootchest.config.LootChestSettingsProvider;
import org.luckyraven.gangland.lootchest.data.CrackingSession;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootTier;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingEndEvent;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingFailureEvent;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingSuccessEvent;
import org.luckyraven.keystone.bean.autowire.Autowired;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.sound.SoundEffect;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Succeeding or failing to crack a chest of an alarm tier rings an alarm at the chest and commits {@code Safe_Cracking}
 * (once per cracking session).
 *
 * @since 0.20.0
 */
@CustomLog
@ListenerHandler
public class CrackingAlarmListener implements Listener {

	private static final AtomicBoolean WARNED = new AtomicBoolean();

	private final LootChestSettingsProvider settings;
	private final CrimeService              crimes;
	private final BiConsumer<Location, String> alarm;
	private final Set<UUID>                 alarmed = ConcurrentHashMap.newKeySet();

	@Autowired
	public CrackingAlarmListener(LootChestSettingsProvider settings, CrimeService crimes) {
		this(settings, crimes, (at, sound) -> {
			// ponytail: a fresh SoundEffect per ring; cache one if alarms ever ring in a tight loop
			boolean played = new SoundEffect(SoundEffect.SoundType.VANILLA, sound, 1.0f, 1.0f).playAtLocation(at);
			if (!played && WARNED.compareAndSet(false, true)) {
				log.warn("Cracking alarm sound '{}' did not play; check Cracking_Alarm.Sound in the lootchest config", sound);
			}
		});
	}

	/** @param alarm plays the named sound at the location (a test seam; the bean plays a Keystone SoundEffect) */
	CrackingAlarmListener(LootChestSettingsProvider settings, CrimeService crimes, BiConsumer<Location, String> alarm) {
		this.settings = settings;
		this.crimes   = crimes;
		this.alarm    = alarm;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSuccess(LootChestCrackingSuccessEvent event) {
		ring(event.getCrackingSession());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onFailure(LootChestCrackingFailureEvent event) {
		ring(event.getCrackingSession());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEnd(LootChestCrackingEndEvent event) {
		alarmed.remove(event.getCrackingSession().getSessionId());
	}

	private void ring(CrackingSession session) {
		CrackingAlarmSettings cfg = settings.getCrackingAlarm();
		if (!cfg.enabled()) {
			return;
		}

		LootTier tier = session.getTier();
		if (tier == null || !cfg.tiers().contains(tier.id().toLowerCase(Locale.ROOT))) {
			return;
		}

		LootChestData chest = session.getChestData();
		Location at = chest == null ? null : chest.getLocation();
		if (at == null || !alarmed.add(session.getSessionId())) {
			return;
		}

		Player player = session.getPlayer();
		crimes.commit(player, Crimes.SAFE_CRACKING, at, false, 1);
		if (at.getWorld() != null) {
			alarm.accept(at, cfg.sound());
		}
		log.debug("CRACK_ALARM {} tier={}", player.getName(), tier.id());
	}

}
