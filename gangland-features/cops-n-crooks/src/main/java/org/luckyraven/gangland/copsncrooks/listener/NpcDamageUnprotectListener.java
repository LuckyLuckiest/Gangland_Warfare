package org.luckyraven.gangland.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.gangland.civilians.npc.entity.EntityMarks;
import org.luckyraven.keystone.bean.autowire.AutowireTarget;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcMetadata;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;

/**
 * Strips Citizens spawn protection from Gangland NPCs so they become damageable as quickly as possible.
 *
 * <p>Protection is stripped at three points:
 * <ol>
 *   <li><b>Spawn-time</b> — {@link NpcSpawnUnprotectListener} (split out T-KR4, review M4: its
 *       {@code @EventHandler} parameter type is Citizens' {@code NPCSpawnEvent}, so it needs its own
 *       {@code condition = "isCitizensAvailable"} gate — see that class's javadoc).</li>
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
@AutowireTarget({NpcMarkManager.class})
public class NpcDamageUnprotectListener implements Listener {

	private final NpcMarkManager markManager;

	/**
	 * Strips Citizens protection at LOW priority (before Citizens' HIGHEST handler) so that Citizens sees the NPC as
	 * unprotected and does not cancel the damage event. If Citizens still cancels despite stripping, the event stays
	 * cancelled — no damage, no hostility.
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
	public void onNpcDamage(EntityDamageEvent event) {
		// T-KR4 (review M4): every CitizensAPI reach below is unguarded without this — the whole handler is
		// meaningless without Citizens (there are no Citizens NPCs to strip protection from).
		if (!NpcSupport.available()) return;

		Entity entity = event.getEntity();
		if (!CitizensAPI.getNPCRegistry().isNPC(entity)) {
			return;
		}

		EntityMark mark = EntityMarks.of(markManager.getMark(entity));
		if (mark == EntityMark.UNSET) {
			return;
		}

		NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (CitizensBridge.isShopNpc(npc)) {
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
		// T-KR4 (review M4): every CitizensAPI reach below is unguarded without this.
		if (!NpcSupport.available()) return;

		Entity entity = event.getHitEntity();
		if (entity == null) {
			return;
		}

		if (!CitizensAPI.getNPCRegistry().isNPC(entity)) {
			return;
		}

		EntityMark mark = EntityMarks.of(markManager.getMark(entity));
		if (mark == EntityMark.UNSET) {
			return;
		}

		NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (CitizensBridge.isShopNpc(npc)) {
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
	 * Citizens-typed helpers live on a nested class, not this listener directly (D2/D-fix-1): this class has no
	 * {@code condition = "isCitizensAvailable"} gate (its two handlers take plain Bukkit/Bartizan event types and
	 * must stay registered unconditionally), so a Citizens type in one of ITS OWN declared method signatures would
	 * still crash the reflective listener scan on a Citizens-less server. A nested class is a separate
	 * {@code Class} object — {@code NpcDamageUnprotectListener.class.getMethods()} never resolves it.
	 */
	private static final class CitizensBridge {

		/**
		 * True for a trader or banker NPC — both opt out of strip-protection (T-J4: replaced the single
		 * trader-metadata check, which required importing a now-cross-module npc-shops type, with the shared
		 * {@link NpcMetadata} keys; also closes the pre-existing gap where a banker's own {@code Invulnerable}
		 * setting was never honored here).
		 */
		private static boolean isShopNpc(NPC npc) {
			return npc != null && (npc.data().has(NpcMetadata.TRADER_ID) || npc.data().has(NpcMetadata.BANKER_ID));
		}
	}
}
