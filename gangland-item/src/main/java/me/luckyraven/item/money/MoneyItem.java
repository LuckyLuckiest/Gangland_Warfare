package me.luckyraven.item.money;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable description of a single cash variation as configured in {@code money.yml}.
 *
 * <p>A variation defines:
 * <ul>
 *   <li>How the dropped {@code ItemStack} looks (material, custom model data, display name, lore, glow).</li>
 *   <li>The min/max amount range that {@link MoneyAddon#rollAmount} samples from when an item is built.</li>
 *   <li>Optional pickup feedback (sound).</li>
 * </ul>
 */
@Getter
public class MoneyItem {

	private final String       id;
	private final Material     material;
	private final int          customModelData;
	private final String       displayName;
	private final List<String> lore;
	private final int          min;
	private final int          max;
	private final boolean      glow;

	@Nullable
	private final Sound pickupSound;

	public MoneyItem(String id,
	                 Material material,
	                 int customModelData,
	                 String displayName,
	                 List<String> lore,
	                 int min,
	                 int max,
	                 boolean glow,
	                 @Nullable Sound pickupSound) {
		this.id              = id;
		this.material        = material;
		this.customModelData = customModelData;
		this.displayName     = displayName;
		this.lore            = lore;
		this.min             = Math.max(0, min);
		this.max             = Math.max(this.min, max);
		this.glow            = glow;
		this.pickupSound     = pickupSound;
	}

}
