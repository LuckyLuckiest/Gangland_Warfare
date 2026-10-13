package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;

import java.util.UUID;
import java.util.function.Supplier;

/** The latest suspect description per player, and how to build, compare and speak one (0.20.0 lane disguise). */
public class SuspectDescriptions {

	/** The weapon text of a suspect holding nothing. */
	public static final String EMPTY_HANDS = "empty hands";

	public SuspectDescriptions(Supplier<DisguiseSettings> settings, WearableKeys keys, WantedMessages messages) {
	}

	/** What {@code player} looks like right now. */
	public SuspectDescription describe(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** At least {@code needed} of head, chest and vehicle of {@code player} right now equal those of {@code description}. */
	public boolean matches(Player player, SuspectDescription description, int needed) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The radio text, e.g. {@code unknown suspect in a carved pumpkin, red jacket, on foot}. */
	public String format(SuspectDescription description) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public void store(UUID playerId, SuspectDescription description) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public @Nullable SuspectDescription latest(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public void clear(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
