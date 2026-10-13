package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The AI tick hook: keeps the description current while seen and radios it once per change (0.20.0 lane disguise). */
public class DisguiseTick {

	private static final String LINE = "Suspect_Description";

	private final Supplier<DisguiseSettings>        settings;
	private final SuspectDescriptions               descriptions;
	private final CopRadio                          radio;
	private final Map<UUID, SuspectDescription>     spoken  = new ConcurrentHashMap<>();
	private final Map<NpcSquad, UUID>               ticked  = new ConcurrentHashMap<>();

	public DisguiseTick(Supplier<DisguiseSettings> settings, SuspectDescriptions descriptions, CopRadio radio) {
		this.settings     = settings;
		this.descriptions = descriptions;
		this.radio        = radio;
	}

	public void tick(Player player, @Nullable CopGroup group) {
		UUID id = player.getUniqueId();

		if (group == null) {
			spoken.remove(id);
			ticked.values().removeIf(id::equals);
			return;
		}
		if (!settings.get().enabled()) {
			return;
		}

		NpcSquad squad = group.getSquad();
		if (squad != null) {
			ticked.put(squad, id);
			if (squad.hasFreshSighting()) {
				descriptions.store(id, descriptions.describe(player));
			}
		}

		SuspectDescription latest = descriptions.latest(id);
		if (latest == null || latest.equals(spoken.get(id))) {
			return;
		}
		if (radio.sayFromLeader(group, LINE)) {
			spoken.put(id, latest);
		}
	}

	/** Forgets the line spoken for {@code playerId}: the chase ended, so the next chase says it again. */
	public void forget(UUID playerId) {
		spoken.remove(playerId);
		ticked.values().removeIf(playerId::equals);
	}

	/** The {@code %suspect%} placeholder for {@code squad}; empty when unknown. */
	public Map<String, String> extras(NpcSquad squad) {
		UUID               id     = ticked.get(squad);
		SuspectDescription latest = id == null ? null : descriptions.latest(id);

		return latest == null ? Map.of() : Map.of("suspect", descriptions.format(latest));
	}
}
