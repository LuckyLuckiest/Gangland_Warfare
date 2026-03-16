package me.luckyraven.weapon.repair.material;

import lombok.Getter;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.repair.RepairKeys;
import me.luckyraven.weapon.repair.RepairableType;
import me.luckyraven.weapon.repair.config.RepairMaterialData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Getter
public class RepairMaterial {

	private final RepairMaterialData data;

	public RepairMaterial(@NotNull RepairMaterialData data) {
		this.data = data;
	}

	public static boolean isRepairMaterial(@Nullable ItemStack item) {
		if (item == null || item.getType() == Material.AIR) return false;
		return new ItemBuilder(item).hasNBTTag(RepairKeys.REPAIR_MATERIAL_ID);
	}

	@Nullable
	public static String getMaterialId(@Nullable ItemStack item) {
		if (!isRepairMaterial(item)) return null;
		return new ItemBuilder(item).getStringTagData(RepairKeys.REPAIR_MATERIAL_ID);
	}

	public static int getCurrentUses(@NotNull ItemStack item) {
		ItemBuilder builder = new ItemBuilder(item);
		if (!builder.hasNBTTag(RepairKeys.REPAIR_MATERIAL_USES)) return 0;
		return builder.getIntegerTagData(RepairKeys.REPAIR_MATERIAL_USES);
	}

	public static boolean hasUsesLeft(@NotNull ItemStack item) {
		return getCurrentUses(item) > 0;
	}

	// Static utilities

	@Nullable
	public static ItemStack consumeUse(@NotNull ItemStack item) {
		ItemBuilder builder = new ItemBuilder(item);
		if (!builder.hasNBTTag(RepairKeys.REPAIR_MATERIAL_USES)) return null;

		int currentUses = builder.getIntegerTagData(RepairKeys.REPAIR_MATERIAL_USES);
		int newUses     = currentUses - 1;

		if (newUses <= 0) return null;

		int maxUses = builder.getIntegerTagData(RepairKeys.REPAIR_MATERIAL_MAX_USES);
		builder.modifyTag(RepairKeys.REPAIR_MATERIAL_USES, newUses);

		if (maxUses > 1) {
			String displayName  = builder.getDisplayName();
			int    bracketIndex = displayName != null ? displayName.lastIndexOf(" &8[") : -1;
			String baseName     = (bracketIndex >= 0) ? displayName.substring(0, bracketIndex) : displayName;
			builder.setDisplayName(baseName + " &8[&7" + newUses + "&8/&7" + maxUses + "&8]");

			List<String> lore = builder.getLore();
			if (lore != null) {
				for (int i = 0; i < lore.size(); i++) {
					if (lore.get(i).contains("Uses:")) {
						lore.set(i, "&7Uses: &e" + newUses + "&7/&e" + maxUses);
						break;
					}
				}
				builder.setLore(lore);
			}
		}

		return builder.build();
	}

	public String getId() {
		return data.getId();
	}

	public boolean isCompatibleWith(@NotNull RepairableType type) {
		return data.isCompatibleWith(type);
	}

	@NotNull
	public ItemStack buildItem() {
		return buildItem(data.getMaxUses());
	}

	@NotNull
	public ItemStack buildItem(int currentUses) {
		ItemBuilder builder = new ItemBuilder(data.getMaterial());

		String displayName = data.getDisplayName();
		if (data.getMaxUses() > 1) {
			displayName = displayName + " &8[&7" + currentUses + "&8/&7" + data.getMaxUses() + "&8]";
		}
		builder.setDisplayName(displayName);

		List<String> lore = new ArrayList<>(data.getLore());
		if (data.getMaxUses() > 1) {
			lore.add("&7Uses: &e" + currentUses + "&7/&e" + data.getMaxUses());
		}
		builder.setLore(lore);

		if (data.getCustomModelData() > 0) {
			builder.setCustomModelData(data.getCustomModelData());
		}

		builder.addTag(RepairKeys.REPAIR_MATERIAL_ID, data.getId());
		builder.addTag(RepairKeys.REPAIR_MATERIAL_USES, currentUses);
		builder.addTag(RepairKeys.REPAIR_MATERIAL_MAX_USES, data.getMaxUses());

		return builder.build();
	}
}
