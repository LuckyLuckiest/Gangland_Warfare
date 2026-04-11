package me.luckyraven.copsncrooks.npc.police.npc;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.AbstractNpc;
import me.luckyraven.copsncrooks.npc.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.npc.police.config.CopTierConfig;
import me.luckyraven.copsncrooks.npc.police.state.CopBehavior;
import me.luckyraven.copsncrooks.npc.police.state.CopState;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@CustomLog
public class CopNpc extends AbstractNpc {

	@Getter
	private final CopTierConfig              tierConfig;
	private final Map<CopState, CopBehavior> behaviors;
	/**
	 * Non-player entities that attacked this cop while it was focused on a player. Engaged in order after the player
	 * target is resolved — cops finish their primary job first.
	 */
	private final Deque<LivingEntity>        pendingEntityAttackers = new ArrayDeque<>();
	@Getter
	private       CopState                   currentState           = CopState.IDLE;
	@Getter
	@Setter
	private       UUID                       targetPlayerId;
	@Getter
	@Setter
	private       LivingEntity               targetEntity;
	@Getter
	@Setter
	private       boolean                    combatForced;

	public CopNpc(NPC npc, CopTierConfig tierConfig, Map<CopState, CopBehavior> behaviors,
	              Location spawnLocation, CopConfigProvider configProvider) {
		super(npc, spawnLocation, configProvider, tierConfig.difficulty());
		this.tierConfig   = tierConfig;
		this.behaviors    = behaviors;
		this.combatForced = false;
	}

	// ── AbstractGanglandNpc contract ─────────────────────────────────────────

	@Override
	public boolean canUseWeapons() {
		return tierConfig.canUseWeapons();
	}

	@Override
	public double getAttackDamage() {
		return tierConfig.damage();
	}

	@Override
	public void equip() {
		if (!isValid()) return;
		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return;

		equipment.setHelmet(tierConfig.helmet());
		equipment.setChestplate(tierConfig.chestplate());
		equipment.setLeggings(tierConfig.leggings());
		equipment.setBoots(tierConfig.boots());

		if (heldWeapon != null) {
			equipment.setItemInMainHand(heldWeapon.buildItem());
		} else if (!tierConfig.weaponPool().isEmpty()) {
			int index = ThreadLocalRandom.current().nextInt(tierConfig.weaponPool().size());
			equipment.setItemInMainHand(tierConfig.weaponPool().get(index));
		}

		if (!(livingEntity instanceof Player)) {
			equipment.setHelmetDropChance(0f);
			equipment.setChestplateDropChance(0f);
			equipment.setLeggingsDropChance(0f);
			equipment.setBootsDropChance(0f);
			equipment.setItemInMainHandDropChance(0f);
		}
	}

	/**
	 * Transitions the cop to a new AI state, invoking exit/enter callbacks.
	 */
	public void transitionTo(CopState newState) {
		log.debug("Transitioning cop {}-{} from {} state to {} state.", npc.getName(), npc.getId(),
		          currentState, newState);
		if (currentState == newState) return;

		CopBehavior oldBehavior = behaviors.get(currentState);
		if (oldBehavior != null) oldBehavior.onExit(this);

		currentState = newState;

		CopBehavior newBehavior = behaviors.get(currentState);
		if (newBehavior == null) return;
		newBehavior.onEnter(this);
	}

	// ── Cop-specific state machine ────────────────────────────────────────────

	/**
	 * Runs a single AI tick using the current state's behavior.
	 *
	 * @param target the resolved target — may be a {@link Player} (wanted player) or any
	 *        {@link org.bukkit.entity.LivingEntity} (e.g. a wanted hostile civilian). Pass {@code null} when no target is
	 * 		available.
	 */
	public void tick(LivingEntity target) {
		if (!isValid()) {
			markForRemoval();
			return;
		}

		// Store target so behaviors can access it.
		// PLAYER-type NPC entities pass instanceof Player but must be stored as entity targets —
		// Bukkit.getPlayer(npcUuid) returns null, so targetPlayerId would be unresolvable next tick.
		if (target instanceof Player p && !CitizensAPI.getNPCRegistry().isNPC(p)) {
			setTargetPlayerId(p.getUniqueId());
			setTargetEntity(null);
		} else {
			setTargetEntity(target);
			setTargetPlayerId(null);
		}

		decrementAttackCooldown();
		updateNavigationProgress();

		CopBehavior behavior = behaviors.get(currentState);
		if (behavior == null) return;
		behavior.tick(this);
	}

	/**
	 * Returns whether this cop is currently using a ranged weapon.
	 */
	@Override
	public boolean isUsingRangedWeapon() {
		if (!tierConfig.canUseWeapons()) return false;
		return heldWeapon != null || isHoldingVanillaRangedWeapon();
	}

	/**
	 * Attempts to cuff the target player.
	 */
	public boolean attemptCuff(Player player) {
		if (!isValid() || player == null) return false;
		if (!hasLineOfSight(player)) return false;
		return !(distanceTo(player) > tierConfig.cuffRadius());
	}

	/**
	 * Adds an entity that attacked this cop to the pending attacker queue. Duplicates are removed before insertion so
	 * each entity appears at most once.
	 */
	public void addEntityAttacker(LivingEntity attacker) {
		if (attacker == null) return;
		pendingEntityAttackers.remove(attacker);
		pendingEntityAttackers.addLast(attacker);
	}

	/**
	 * Polls the next live entity attacker from the pending queue, removing dead/invalid entries along the way.
	 *
	 * @return the next valid entity attacker, or {@code null} if the queue is empty
	 */
	public LivingEntity pollNextEntityAttacker() {
		while (!pendingEntityAttackers.isEmpty()) {
			LivingEntity candidate = pendingEntityAttackers.poll();
			if (candidate != null && candidate.isValid() && !candidate.isDead()) return candidate;
		}
		return null;
	}

	@Override
	protected void cleanupTransientState() {
		pendingEntityAttackers.clear();
		CopBehavior behavior = behaviors.get(currentState);
		if (behavior == null) return;
		behavior.onExit(this);
	}
}
