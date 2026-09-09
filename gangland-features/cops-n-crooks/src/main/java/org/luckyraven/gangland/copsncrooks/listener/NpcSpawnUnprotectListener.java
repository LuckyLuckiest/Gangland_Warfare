package org.luckyraven.gangland.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcMetadata;

/**
 * Strips Citizens spawn protection from a freshly spawned Gangland NPC (spawn-time step of
 * {@link NpcDamageUnprotectListener}'s three-point strip). Runs every tick for 20 ticks to cover the full Citizens
 * trait initialization window; after that, {@code AbstractNpc.ensureDamageable()} (every AI tick) takes over.
 *
 * <p>Split out of {@link NpcDamageUnprotectListener} (T-KR4, review M4): this class's own {@code @EventHandler}
 * parameter type is Citizens' {@link NPCSpawnEvent}, so scanning/registering it on a Citizens-less server throws
 * {@code NoClassDefFoundError} out of the listener phase — {@code condition = "isCitizensAvailable"} skips
 * construction/registration entirely instead. {@link NpcDamageUnprotectListener}'s other two handlers take plain
 * Bukkit/Bartizan event types and stay registered unconditionally (guarded internally instead), so the class as a
 * whole could not carry this condition without losing them.
 */
@ListenerHandler(condition = "isCitizensAvailable")
@RequiredArgsConstructor
@AutowireTarget({JavaPlugin.class})
public class NpcSpawnUnprotectListener implements Listener {

	private final JavaPlugin plugin;

	/**
	 * Strips protection from every Gangland NPC after spawn. Runs every tick for 20 ticks to cover the full Citizens
	 * trait initialization window. After this window, {@code AbstractNpc.ensureDamageable()} (every AI tick) takes
	 * over.
	 */
	@EventHandler
	public void onNpcSpawn(NPCSpawnEvent event) {
		NPC npc = event.getNPC();

		if (npc.data().get(NPC.Metadata.SHOULD_SAVE) != null
		    && (boolean) npc.data().get(NPC.Metadata.SHOULD_SAVE)) {
			return;
		}

		// Traders/bankers opt out of strip-protection: the Invulnerable trait needs the Citizens/Bukkit flags to
		// stay set.
		if (isShopNpc(npc)) {
			return;
		}

		stripProtection(npc);

		new BukkitRunnable() {
			private int remaining = 20;

			@Override
			public void run() {
				if (!npc.isSpawned() || remaining-- <= 0) {
					cancel();
					return;
				}
				stripProtection(npc);
			}
		}.runTaskTimer(plugin, 1L, 1L);
	}

	/**
	 * True for a trader or banker NPC — both opt out of strip-protection (see
	 * {@link NpcDamageUnprotectListener#isShopNpc(NPC)}, duplicated here rather than shared: two small private
	 * checks across a class the module loader must be able to skip independently of this one).
	 */
	private boolean isShopNpc(NPC npc) {
		return npc != null && (npc.data().has(NpcMetadata.TRADER_ID) || npc.data().has(NpcMetadata.BANKER_ID));
	}

	private void stripProtection(NPC npc) {
		npc.setProtected(false);

		Entity entity = npc.getEntity();
		if (entity == null) return;

		entity.setInvulnerable(false);

		if (entity instanceof LivingEntity living) {
			living.setMaximumNoDamageTicks(0);
			living.setNoDamageTicks(0);
		}
	}
}
