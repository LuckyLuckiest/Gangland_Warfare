package org.luckyraven.gangland.weapon.wearable;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.item.contract.WearableEquipService;
import org.luckyraven.gangland.item.wearable.Wearable;
import org.luckyraven.gangland.item.wearable.WearableTrait;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Central registry and calculation service for {@link Wearable} armor pieces.
 *
 * <p>The concrete implementation in {@code gangland-impl} ({@code WearableAddon}) extends this
 * class and loads wearables from {@code wearables.yml}. This base class is sufficient for use inside
 * {@code gangland-weapon} (e.g. {@code ProjectileDamage}) without a hard dependency on the impl module.
 *
 * <h3>Resolution order for a worn ItemStack:</h3>
 * <ol>
 *   <li>If the item has a {@code "wearable"} NBT key that matches a registered entry → use the
 *       registry's live values (config changes apply to existing items).</li>
 *   <li>If the item has a {@code "wearable"} NBT key but is no longer in the registry (e.g.
 *       removed from config) → create a temporary Wearable from the item's material so the piece
 *       still grants basic protection.</li>
 *   <li>If the item is any vanilla armor piece → create a temporary Wearable from the
 *       material.</li>
 *   <li>Otherwise → null (no reduction applied).</li>
 * </ol>
 */
public class WearableService implements WearableEquipService {

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private final Map<String, Wearable> wearables = new HashMap<>();

	public void register(String key, Wearable wearable) {
		wearables.put(key.toLowerCase(), wearable);
	}

	@Nullable
	public Wearable getWearable(String key) {
		return wearables.get(key.toLowerCase());
	}

	public Map<String, Wearable> getWearables() {
		return Collections.unmodifiableMap(wearables);
	}

	public void clear() {
		wearables.clear();
	}

	/**
	 * Resolves a {@link Wearable} from an ItemStack according to the resolution order described in the class javadoc.
	 *
	 * @param item the armor ItemStack
	 *
	 * @return a Wearable (possibly temporary), or {@code null} if the item is not armor
	 */
	@Nullable
	public Wearable resolveWearable(@Nullable ItemStack item) {
		if (item == null || item.getType().isAir()) return null;

		String key = Wearable.getWearableKey(item);
		if (key != null) {
			Wearable registered = getWearable(key);
			if (registered != null) return registered;
			// Key present but not in registry - fall through to vanilla fallback
		}

		return Wearable.fromItemStack(item);
	}

	/**
	 * Applies all worn-wearable damage reductions for a living entity to the given damage value. Reductions are applied
	 * multiplicatively per armor slot so that wearing four pieces of armor cannot completely negate damage through
	 * stacking alone.
	 *
	 * <p>Each slot's total reduction = wearable reduction + enchantment bonus, capped per piece.
	 *
	 * <p>The {@link WearableTrait#REACTIVE} trait is checked
	 * first; if any piece procs it, the full damage is nullified and processing stops immediately.
	 *
	 * @param damage incoming damage before wearable reduction
	 * @param target the entity wearing the armor
	 * @param isProjectile {@code true} when the damage source is a projectile (enables {@code BULLETPROOF} trait and
	 *        {@code PROJECTILE_PROTECTION} enchantment bonuses)
	 *
	 * @return the final damage value (≥ 0)
	 */
	public double applyWearableReduction(double damage, LivingEntity target, boolean isProjectile) {
		EntityEquipment equipment = target.getEquipment();
		if (equipment == null) return damage;

		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack item = equipment.getItem(slot);
			if (item.getType().isAir()) continue;

			Wearable wearable = resolveWearable(item);
			if (wearable == null) continue;

			// REACTIVE: chance to nullify the entire hit
			if (wearable.rollReactive()) return 0;

			double slotReduction = isProjectile
			                       ? wearable.getProjectileDamageReduction()
			                       : wearable.getGenericDamageReduction();

			double enchBonus = isProjectile
			                   ? Wearable.getEnchantmentProjectileBonus(item)
			                   : Wearable.getEnchantmentGenericBonus(item);

			// Hard-cap the combined reduction for this single slot
			double totalSlotReduction = Math.min(slotReduction + enchBonus, 0.90);

			// Multiplicative stacking
			damage *= (1.0 - totalSlotReduction);
		}

		return Math.max(damage, 0);
	}

	/**
	 * Reduces the bonus damage added by a critical hit, based on the {@link WearableTrait#TOUGHENED} trait across all
	 * worn pieces.
	 *
	 * @param critBonus the raw critical-hit bonus damage
	 * @param target the entity wearing the armor
	 *
	 * @return the reduced critical-hit bonus (≥ 0)
	 */
	public double reduceCritBonus(double critBonus, LivingEntity target) {
		EntityEquipment equipment = target.getEquipment();
		if (equipment == null) return critBonus;

		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack item = equipment.getItem(slot);
			if (item.getType().isAir()) continue;

			Wearable wearable = resolveWearable(item);
			if (wearable == null) continue;

			critBonus *= (1.0 - wearable.getCritBonusReduction());
		}

		return Math.max(critBonus, 0);
	}

	/**
	 * Reduces the number of fire ticks to be applied to the target, based on the {@link WearableTrait#FIRE_RESISTANT}
	 * trait and the vanilla {@code FIRE_PROTECTION} enchantment across all worn armor pieces.
	 *
	 * @param fireTicks the raw fire-tick count from the weapon
	 * @param target the entity wearing the armor
	 *
	 * @return the reduced fire-tick count (≥ 0)
	 */
	public int reduceFireTicks(int fireTicks, LivingEntity target) {
		if (fireTicks <= 0) return 0;

		EntityEquipment equipment = target.getEquipment();
		if (equipment == null) return fireTicks;

		double reduction = 0;

		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack item = equipment.getItem(slot);
			if (item.getType().isAir()) continue;

			Wearable wearable = resolveWearable(item);
			if (wearable != null) {
				reduction += wearable.getFireTickReduction();
			}

			// Vanilla FIRE_PROTECTION enchantment contributes additively
			reduction += Wearable.getEnchantmentFireBonus(item);
		}

		// Cap total fire reduction at 90 %
		reduction = Math.min(reduction, 0.90);

		return (int) Math.max(fireTicks * (1.0 - reduction), 0);
	}

}
