package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** The AI tick hook: keeps the description current while seen and radios it once per change (0.20.0 lane disguise). */
public class DisguiseTick {

	public DisguiseTick(Supplier<DisguiseSettings> settings, SuspectDescriptions descriptions, CopRadio radio) {
	}

	public void tick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Forgets the line spoken for {@code playerId}: the chase ended, so the next chase says it again. */
	public void forget(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The {@code %suspect%} placeholder for {@code squad}; empty when unknown. */
	public Map<String, String> extras(NpcSquad squad) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
