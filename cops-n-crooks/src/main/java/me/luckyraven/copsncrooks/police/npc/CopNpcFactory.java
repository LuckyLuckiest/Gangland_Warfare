package me.luckyraven.copsncrooks.police.npc;

import me.luckyraven.copsncrooks.entity.EntityMark;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.config.CopTierConfig;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopBehaviorFactory;
import me.luckyraven.copsncrooks.police.state.CopState;
import me.luckyraven.util.utilities.ChatUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.Map;

/**
 * Factory for creating CopNpc instances backed by Citizens NPCs.
 */
public class CopNpcFactory {

	private final CopConfigProvider  configProvider;
	private final CopBehaviorFactory behaviorFactory;
	private final EntityMarkManager  entityMarkManager;

	public CopNpcFactory(CopConfigProvider configProvider, EntityMarkManager entityMarkManager,
						 CopSpawnManager spawnManager) {
		this.configProvider    = configProvider;
		this.behaviorFactory   = new CopBehaviorFactory(configProvider, spawnManager);
		this.entityMarkManager = entityMarkManager;
	}

	/**
	 * Creates a new cop NPC at the given location with the specified tier.
	 *
	 * @param spawnLocation the location to spawn the NPC
	 * @param tier the cop tier
	 *
	 * @return the created CopNpc, or null if spawning failed
	 */
	public CopNpc createCop(Location spawnLocation, int tier) {
		CopTierConfig tierConfig = configProvider.getTierConfig(tier);

		NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, ChatUtil.color(tierConfig.displayName()));
		npc.setProtected(false);

		npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);

		npc.spawn(spawnLocation);

		if (!npc.isSpawned()) {
			npc.destroy();
			return null;
		}

		// Mark the entity as POLICE for the entity mark system
		if (npc.getEntity() != null) {
			entityMarkManager.setEntityMark(npc.getEntity(), EntityMark.POLICE);
		}

		Map<CopState, CopBehavior> behaviors = behaviorFactory.createBehaviors();
		CopNpc                     copNpc    = new CopNpc(npc, tierConfig, behaviors, spawnLocation);
		copNpc.equip();

		npc.getNavigator().getLocalParameters().speedModifier((float) tierConfig.speed());

		return copNpc;
	}
}