package org.luckyraven.gangland.copsncrooks.npc.banker;

import lombok.Getter;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.copsncrooks.npc.banker.config.BankerSettings;

public final class BankerNpc {

	public static final String METADATA_BANKER_ID = "gangland.banker.id";

	@Getter
	private final BankerData data;
	@Getter
	private final NPC        npc;

	public BankerNpc(BankerData data, NPC npc) {
		this.data = data;
		this.npc  = npc;
	}

	public static BankerNpc spawn(BankerData data, BankerSettings settings) {
		String name = data.getDisplayName() != null ? data.getDisplayName() : "Banker";

		NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
		// BankerRepository is the sole source of truth — Citizens must not persist bankers to its saves.yml.
		npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
		npc.data().setPersistent(METADATA_BANKER_ID, data.getId().toString());
		npc.setProtected(settings.isInvulnerable());

		npc.spawn(data.getSpawnLocation());

		Entity entity = npc.getEntity();
		if (entity instanceof LivingEntity living) {
			living.setInvulnerable(settings.isInvulnerable());
			living.setGravity(false);

			double            hp   = Math.max(1.0, settings.getMaxHealth());
			AttributeInstance attr = living.getAttribute(Attribute.MAX_HEALTH);
			if (attr != null) attr.setBaseValue(hp);
			living.setHealth(hp);
		}

		return new BankerNpc(data, npc);
	}

	public void faceLocation(Location target) {
		if (!npc.isSpawned()) return;
		npc.faceLocation(target);
	}

	public void resetPosition() {
		if (!npc.isSpawned()) return;
		Entity entity = npc.getEntity();
		if (entity == null) return;

		Location spawn   = data.getSpawnLocation();
		Location current = entity.getLocation();
		if (current.distanceSquared(spawn) > 0.25) {
			entity.teleport(spawn);
		}
	}

	public boolean isAlive() {
		if (!npc.isSpawned()) return false;
		Entity entity = npc.getEntity();
		return entity != null && !entity.isDead();
	}

	public void destroy() {
		if (npc.isSpawned()) npc.despawn();
		npc.destroy();
	}

}
