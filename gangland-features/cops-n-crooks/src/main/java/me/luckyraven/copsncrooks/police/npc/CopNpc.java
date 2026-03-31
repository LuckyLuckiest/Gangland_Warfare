package me.luckyraven.copsncrooks.police.npc;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.entity.npc.AbstractNpc;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.config.CopTierConfig;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopState;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@CustomLog
public class CopNpc extends AbstractNpc {

	@Getter
	private final CopTierConfig              tierConfig;
	private final Map<CopState, CopBehavior> behaviors;

	@Getter
	private CopState currentState = CopState.IDLE;
	@Getter
	@Setter
	private UUID     targetPlayerId;
	@Getter
	@Setter
	private boolean  combatForced;

	public CopNpc(NPC npc, CopTierConfig tierConfig, Map<CopState, CopBehavior> behaviors,
	              Location spawnLocation, CopConfigProvider configProvider) {
		super(npc, spawnLocation, configProvider);
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
	 */
	public void tick(Player target) {
		if (!isValid()) {
			markForRemoval();
			return;
		}

		// Store target so behaviors can access it via getTargetPlayerId()
		setTargetPlayerId(target != null ? target.getUniqueId() : null);

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

	@Override
	protected void cleanupTransientState() {
		CopBehavior behavior = behaviors.get(currentState);
		if (behavior == null) return;
		behavior.onExit(this);
	}
}
