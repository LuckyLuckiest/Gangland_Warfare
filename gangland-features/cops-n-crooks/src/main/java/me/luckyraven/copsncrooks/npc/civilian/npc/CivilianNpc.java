package me.luckyraven.copsncrooks.npc.civilian.npc;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.entity.npc.AbstractNpc;
import me.luckyraven.copsncrooks.npc.civilian.CivilianGroup;
import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianNavigationConfig;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianTypeConfig;
import me.luckyraven.copsncrooks.npc.civilian.state.CivilianBehavior;
import me.luckyraven.item.ItemParser;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@CustomLog
public class CivilianNpc extends AbstractNpc {

	@Getter
	private final           CivilianTypeConfig                   typeConfig;
	@Getter
	private final @Nullable String                               groupId;
	private final           Map<CivilianState, CivilianBehavior> behaviors;
	private final @Nullable ItemParser                           itemParser;

	@Getter
	private CivilianState currentState = CivilianState.IDLE;

	@Getter
	@Setter
	private @Nullable UUID targetPlayerId;

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
		super(npc, spawnLocation, navConfig);
		this.typeConfig = typeConfig;
		this.groupId    = groupId;
		this.behaviors  = behaviors;
		this.itemParser = itemParser;
	}

	// ── AbstractGanglandNpc contract ─────────────────────────────────────────

	@Override
	public boolean canUseWeapons() {
		return typeConfig.hostile() && (!typeConfig.weaponNamePool().isEmpty() || !typeConfig.weaponPool().isEmpty());
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
		log.info("Transitioning civilian {}-{} from {} state to {} state.", npc.getName(), npc.getId(), currentState,
		         newState);
		if (currentState == newState) return;

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
		return typeConfig.hostile();
	}

	@Override
	protected void cleanupTransientState() {
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
