package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;
import me.luckyraven.weapon.types.throwable.ThrowableType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThrowableData implements Cloneable {

	private int     fuseTime;
	private double  explosionRadius;
	private int     explosionDamage;
	private int     fireTicks;
	private boolean bounces;
	private int     maxBounces;
	private boolean sticky;
	private String  entityType;

	/**
	 * Behaviour discriminator. Defaults to {@link ThrowableType#EXPLOSIVE} when missing from yml so existing throwable
	 * configs keep their legacy detonation behaviour.
	 */
	private ThrowableType type;
	/**
	 * Potion effect tokens applied on detonation. Used by {@link ThrowableType#STUN} (instant on every entity in
	 * radius) and {@link ThrowableType#SMOKE} (re-applied periodically while the cloud lasts). Parsed via
	 * {@link me.luckyraven.weapon.util.PotionEffectParser}.
	 */
	private List<String>  effects;
	/**
	 * Lifetime of a smoke cloud in ticks. Only meaningful when {@link #type} is {@link ThrowableType#SMOKE}.
	 */
	private int           cloudDuration;
	/**
	 * Radius of a smoke cloud in blocks. Only meaningful when {@link #type} is {@link ThrowableType#SMOKE}; defaults to
	 * {@link #explosionRadius} when zero.
	 */
	private double        cloudRadius;
	/**
	 * Item visually shown on the airborne thrown entity. When {@code null}, falls back to the weapon's held material.
	 */
	@Nullable
	private ItemStack     displayItem;

	@Override
	public ThrowableData clone() {
		try {
			ThrowableData clone = (ThrowableData) super.clone();
			if (effects != null) clone.effects = new ArrayList<>(effects);
			if (displayItem != null) clone.displayItem = displayItem.clone();
			return clone;
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

}
