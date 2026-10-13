package org.luckyraven.gangland.lootchest.listener;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.lootchest.config.LootChestSettingsProvider;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingEndEvent;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingFailureEvent;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingSuccessEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

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

	public CrackingAlarmListener(LootChestSettingsProvider settings, CrimeService crimes) {
		this(settings, crimes, (at, sound) -> {
		});
	}

	/** @param alarm plays the named sound at the location (a test seam; the bean plays a Keystone SoundEffect) */
	CrackingAlarmListener(LootChestSettingsProvider settings, CrimeService crimes, BiConsumer<Location, String> alarm) {
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSuccess(LootChestCrackingSuccessEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onFailure(LootChestCrackingFailureEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEnd(LootChestCrackingEndEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
