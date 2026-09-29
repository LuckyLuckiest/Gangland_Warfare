package org.luckyraven.gangland.civilians.npc.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.entity.EntityType;

/**
 * Creates Gangland's Citizens NPCs. A plain static utility, never scanned as a bean, so its signatures may name
 * Citizens types (same reasoning as Keystone's {@code NpcSupport}); callers reach it only after
 * {@code NpcSupport.available()}.
 */
public final class CitizensNpcs {

	private CitizensNpcs() {
	}

	/**
	 * Creates (does not spawn) an NPC through the live Citizens registry. A PLAYER NPC gets its default skin fetch
	 * disabled: Citizens would otherwise fetch the Mojang skin named after the NPC (e.g. "Officer") and, when it
	 * arrives, {@code Skin.applyAndRespawn} despawns and respawns the NPC — a new entity without the health, mark and
	 * equipment put on the spawned one, and a navigation reset mid-pursuit.
	 */
	public static NPC create(EntityType type, String name) {
		NPC npc = CitizensAPI.getNPCRegistry().createNPC(type, name);
		if (type == EntityType.PLAYER) npc.getOrAddTrait(SkinTrait.class).setFetchDefaultSkin(false);
		return npc;
	}
}
