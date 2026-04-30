package org.luckyraven.gangland.copsncrooks.npc.trader;

import lombok.Getter;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.copsncrooks.npc.trader.trait.TraderTraitProfile;

public final class TraderNpc {

	public static final String METADATA_TRADER_ID = "gangland.trader.id";

	@Getter
	private final TraderData data;
	@Getter
	private final NPC        npc;

	public TraderNpc(TraderData data, NPC npc) {
		this.data = data;
		this.npc  = npc;
	}

	public static TraderNpc spawn(TraderData data, TraderTraitProfile trait) {
		String name = data.getDisplayName() != null ? data.getDisplayName() : "Trader";

		NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
		// Citizens must NOT persist our traders in its own saves.yml — TraderRepository is the sole
		// source of truth. Matches the pattern used for cops in CopNpcFactory.java:81.
		npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
		npc.data().setPersistent(METADATA_TRADER_ID, data.getId().toString());
		npc.setProtected(trait.invulnerable());

		npc.spawn(data.getSpawnLocation());

		Entity entity = npc.getEntity();
		if (entity instanceof LivingEntity living) {
			living.setInvulnerable(trait.invulnerable());
			living.setGravity(false);

			double            hp   = Math.max(1.0, trait.maxHealth());
			AttributeInstance attr = living.getAttribute(Attribute.MAX_HEALTH);
			if (attr != null) attr.setBaseValue(hp);
			living.setHealth(hp);
		}

		return new TraderNpc(data, npc);
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
