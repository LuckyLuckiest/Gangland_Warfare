package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;

import java.util.UUID;
import java.util.function.Supplier;

/** The police outfit: worn, and "blown" by a crime committed in it until it comes off (0.20.0 lane disguise). */
public class PoliceOutfit {

	public PoliceOutfit(Supplier<DisguiseSettings> settings, WearableKeys keys) {
	}

	/** Every configured outfit key is on the helmet or chest slot of {@code player}. */
	public boolean worn(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** A crime was committed: blown when the outfit is worn. */
	public void onCrime(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Worn, not blown, and {@code level} within {@code Outfit_Max_Level}; a removed outfit forgets its blown flag. */
	public boolean hides(Player player, int level) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public void clear(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The Bartizan-backed keys; the only place a Bartizan type is named. */
	public static WearableKeys bartizanKeys() {
		throw new UnsupportedOperationException("TDD stub");
	}
}
