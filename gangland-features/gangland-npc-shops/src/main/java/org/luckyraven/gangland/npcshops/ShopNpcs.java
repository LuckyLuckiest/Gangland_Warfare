package org.luckyraven.gangland.npcshops;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** Lifecycle operations shared by the banker and trader NPC wrappers over a Citizens {@link NPC}. */
public final class ShopNpcs {

	private ShopNpcs() {
	}

	public static void faceLocation(NPC npc, Location target) {
		if (!npc.isSpawned()) return;
		npc.faceLocation(target);
	}

	public static void resetPosition(NPC npc, Location spawn) {
		if (!npc.isSpawned()) return;
		Entity entity = npc.getEntity();
		if (entity == null) return;

		Location current = entity.getLocation();
		if (current.distanceSquared(spawn) > 0.25) {
			entity.teleport(spawn);
		}
	}

	public static boolean isAlive(NPC npc) {
		if (!npc.isSpawned()) return false;
		Entity entity = npc.getEntity();
		return entity != null && !entity.isDead();
	}

	public static void destroy(NPC npc) {
		if (npc.isSpawned()) npc.despawn();
		npc.destroy();
	}
}
