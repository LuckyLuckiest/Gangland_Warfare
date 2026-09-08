package org.luckyraven.gangland.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.luckyraven.gangland.copsncrooks.npc.entity.EntityMark;
import org.luckyraven.gangland.copsncrooks.npc.entity.EntityMarkManager;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcMetadata;
import org.luckyraven.gangland.weapon.events.projectile.WeaponRaytraceImpactEvent;

/**
 * Strips Citizens spawn protection from Gangland NPCs so they become damageable as quickly as possible.
 *
 * <p>Protection is stripped at three points:
 * <ol>
 *   <li><b>Spawn-time</b> — a repeating task strips protection every tick for 20 ticks after spawn,
 *       covering the full Citizens trait initialization window.</li>
 *   <li><b>Pre-damage (raytrace)</b> — strips protection on {@link WeaponRaytraceImpactEvent} before
 *       the raytracer calls {@code living.damage()}.</li>
 *   <li><b>During damage</b> — a {@code LOW}-priority handler strips protection before Citizens'
 *       {@code HIGHEST} handler evaluates it.</li>
 * </ol>
 *
 * <p>If Citizens still blocks the damage (e.g. during post-spawn initialization), the event stays
 * cancelled and the NPC does not become hostile — no damage means no reaction.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({EntityMarkManager.class, JavaPlugin.class})
public class NpcDamageUnprotectListener implements Listener {

	private final EntityMarkManager entityMarkManager;
	private final JavaPlugin        plugin;

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
	 * Strips Citizens protection at LOW priority (before Citizens' HIGHEST handler) so that Citizens sees the NPC as
	 * unprotected and does not cancel the damage event. If Citizens still cancels despite stripping, the event stays
	 * cancelled — no damage, no hostility.
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
	public void onNpcDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();
		if (!CitizensAPI.getNPCRegistry().isNPC(entity)) {
			return;
		}

		EntityMark mark = entityMarkManager.getEntityMark(entity);
		if (mark == EntityMark.UNSET) {
			return;
		}

		NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (isShopNpc(npc)) {
			return;
		}
		if (npc != null && npc.isProtected()) {
			npc.setProtected(false);
		}

		entity.setInvulnerable(false);

		if (entity instanceof LivingEntity living) {
			living.setNoDamageTicks(0);
		}
	}

	/**
	 * Strips Citizens protection before the raytracer calls {@code living.damage()}. The impact event fires first, then
	 * the raytracer applies the default damage pipeline.
	 */
	@EventHandler(priority = EventPriority.NORMAL)
	public void onWeaponImpact(WeaponRaytraceImpactEvent event) {
		Entity entity = event.getHitEntity();
		if (entity == null) {
			return;
		}

		if (!CitizensAPI.getNPCRegistry().isNPC(entity)) {
			return;
		}

		EntityMark mark = entityMarkManager.getEntityMark(entity);
		if (mark == EntityMark.UNSET) {
			return;
		}

		NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (isShopNpc(npc)) {
			return;
		}
		if (npc != null) {
			npc.setProtected(false);
		}

		entity.setInvulnerable(false);

		if (entity instanceof LivingEntity living) {
			living.setNoDamageTicks(0);
		}
	}

	/**
	 * True for a trader or banker NPC — both opt out of strip-protection (T-J4: replaced the single trader-metadata
	 * check, which required importing a now-cross-module npc-shops type, with the shared {@link NpcMetadata} keys;
	 * also closes the pre-existing gap where a banker's own {@code Invulnerable} setting was never honored here).
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
