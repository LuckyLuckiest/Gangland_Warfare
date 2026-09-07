package org.luckyraven.gangland.copsncrooks.npc.civilian.npc;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.AbstractNpc;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianGroup;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianState;
import org.luckyraven.gangland.copsncrooks.npc.civilian.config.CivilianNavigationConfig;
import org.luckyraven.gangland.copsncrooks.npc.civilian.config.CivilianTypeConfig;
import org.luckyraven.gangland.copsncrooks.npc.civilian.state.CivilianBehavior;
import org.luckyraven.gangland.item.ItemParser;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@CustomLog
public class CivilianNpc extends AbstractNpc {

	@Getter
	private final           CivilianTypeConfig                   typeConfig;
	@Getter
	private final @Nullable String                               groupId;
	private final           Map<CivilianState, CivilianBehavior> behaviors;
	private final @Nullable ItemParser                           itemParser;
	/**
	 * Queue of non-player entity targets for NPC-to-NPC combat. The head of the queue is the current active entity
	 * target. Entity targets take priority over the player target (self-defense). Use {@link #addEntityTargetToFront}
	 * to bump an attacker to the front.
	 */
	private final           Deque<LivingEntity>                  entityTargetQueue = new ArrayDeque<>();
	@Getter
	private                 CivilianState                        currentState      = CivilianState.IDLE;
	@Getter
	@Setter
	private @Nullable       UUID                                 targetPlayerId;
	/**
	 * True when this hostile civilian is actively in combat — used by cops to identify wanted NPCs.
	 */
	@Getter
	private                 boolean                              wantedByPolice;

	/**
	 * Location of the last entity that damaged this NPC — used by flee behavior.
	 */
	@Getter
	@Setter
	private @Nullable Location lastAttackerLocation;

	/**
	 * The group this NPC belongs to, if any. Set by the factory after all members are created.
	 */
	@Getter
	@Setter
	private @Nullable CivilianGroup group;

	/**
	 * The spawner ID that created this NPC via proximity spawning, or {@code null} if spawned manually.
	 */
	@Getter
	@Setter
	private @Nullable Integer spawnerId;

	public CivilianNpc(NPC npc, CivilianTypeConfig typeConfig, @Nullable String groupId,
	                   Map<CivilianState, CivilianBehavior> behaviors, Location spawnLocation,
	                   CivilianNavigationConfig navConfig, @Nullable ItemParser itemParser) {
		super(npc, spawnLocation, navConfig, typeConfig.ai().difficulty());
		this.typeConfig = typeConfig;
		this.groupId    = groupId;
		this.behaviors  = behaviors;
		this.itemParser = itemParser;
	}

	// ── AbstractGanglandNpc contract ─────────────────────────────────────────

	@Override
	public boolean canUseWeapons() {
		return isHostile() && (!typeConfig.weaponNamePool().isEmpty() || !typeConfig.weaponPool().isEmpty());
	}

	@Override
	public double getAttackDamage() {
		return typeConfig.ai().attackDamage();
	}

	@Override
	public void equip() {
		if (!isValid()) return;
		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return;

		equipment.setHelmet(resolveItem(typeConfig.wearables().helmet()));
		equipment.setChestplate(resolveItem(typeConfig.wearables().chestplate()));
		equipment.setLeggings(resolveItem(typeConfig.wearables().leggings()));
		equipment.setBoots(resolveItem(typeConfig.wearables().boots()));

		if (heldWeapon != null) {
			equipment.setItemInMainHand(heldWeapon.buildItem());
		} else if (!typeConfig.weaponPool().isEmpty()) {
			int index = ThreadLocalRandom.current().nextInt(typeConfig.weaponPool().size());
			equipment.setItemInMainHand(typeConfig.weaponPool().get(index));
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
	 * Transitions the NPC to a new AI state, invoking exit/enter callbacks.
	 */
	public void transitionTo(CivilianState newState) {
		log.debug("Transitioning civilian {}-{} from {} state to {} state.", npc.getName(), npc.getId(), currentState,
		          newState);
		if (currentState == newState) return;

		// Update wanted-by-police flag: hostile civilians become wanted when entering combat
		if (isHostile()) {
			wantedByPolice = (newState == CivilianState.COMBAT);
		}

		CivilianBehavior oldBehavior = behaviors.get(currentState);
		if (oldBehavior != null) oldBehavior.onExit(this);

		currentState = newState;

		CivilianBehavior newBehavior = behaviors.get(currentState);
		if (newBehavior != null) newBehavior.onEnter(this);
	}

	// ── State machine ─────────────────────────────────────────────────────────

	/**
	 * Runs one AI tick for this civilian.
	 */
	public void tick() {
		if (!isValid()) {
			markForRemoval();
			return;
		}

		// If the entity has been teleported to a different world (e.g. through a portal), remove it
		// so the spawner can replace it rather than waiting indefinitely for it to die elsewhere.
		LivingEntity entity = getEntity();
		if (entity != null && spawnLocation.getWorld() != null && !entity.getWorld().equals(spawnLocation.getWorld())) {
			markForRemoval();
			return;
		}

		decrementAttackCooldown();
		updateNavigationProgress();

		CivilianBehavior behavior = behaviors.get(currentState);
		if (behavior == null) return;
		behavior.tick(this);
	}

	/**
	 * Returns whether this civilian is configured as hostile.
	 */
	public boolean isHostile() {
		return typeConfig.hostile() || (group != null && group.getConfig().hostile());
	}

	/**
	 * Returns the current active entity target (head of queue), skipping and removing any stale entries. Entity targets
	 * take priority over the player target — civilians fight back before resuming offence.
	 */
	@Nullable
	public LivingEntity getTargetEntity() {
		while (!entityTargetQueue.isEmpty()) {
			LivingEntity head = entityTargetQueue.peek();
			if (head != null && head.isValid() && !head.isDead()) return head;
			entityTargetQueue.poll();
		}
		return null;
	}

	// ── Entity target queue ───────────────────────────────────────────────────

	/**
	 * Replaces the entire entity target queue with a single entity. Pass {@code null} to clear all entity targets.
	 */
	public void setTargetEntity(@Nullable LivingEntity entity) {
		entityTargetQueue.clear();
		if (entity != null) entityTargetQueue.addLast(entity);
	}

	/**
	 * Inserts {@code entity} at the front of the entity target queue, making it the immediate target. Duplicate entries
	 * are removed before insertion so the entity appears exactly once. Use this when the civilian is attacked — the
	 * attacker becomes the highest-priority target.
	 */
	public void addEntityTargetToFront(LivingEntity entity) {
		if (entity == null) return;
		entityTargetQueue.remove(entity);
		entityTargetQueue.addFirst(entity);
	}

	@Override
	protected void cleanupTransientState() {
		entityTargetQueue.clear();
		wantedByPolice = false;
		CivilianBehavior behavior = behaviors.get(currentState);
		if (behavior == null) return;
		behavior.onExit(this);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	/**
	 * Resolves an item string via ItemParser if available, with XMaterial fallback.
	 */
	@Nullable
	private ItemStack resolveItem(@Nullable String entry) {
		if (entry == null || entry.isBlank()) return null;

		if (itemParser != null) return itemParser.parse(entry);

		try {
			Optional<XMaterial> xMat = XMaterial.matchXMaterial(entry.toUpperCase());
			if (xMat.isPresent()) {
				Material mat = xMat.get().get();
				if (mat != null) return new ItemStack(mat);
			}
		} catch (IllegalArgumentException ignored) {
		}

		return null;
	}
}
