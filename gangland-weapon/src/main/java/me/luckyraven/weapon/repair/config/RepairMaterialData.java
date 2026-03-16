package me.luckyraven.weapon.repair.config;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.repair.RepairableType;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
@Builder
public class RepairMaterialData {

	@NotNull
	private final String id;

	@NotNull
	private final String displayName;

	@NotNull
	private final Material material;

	private final int maxUses;

	private final int restoreAmount;

	private final double restorePercent;

	@Nullable
	private final SoundConfiguration repairDefaultSound;

	@Nullable
	private final SoundConfiguration repairCustomSound;

	@NotNull
	private final List<String> lore;

	private final int customModelData;

	@NotNull
	private final List<RepairableType> compatibleTypes;

	public boolean isCompatibleWith(@NotNull RepairableType type) {
		return compatibleTypes.contains(type);
	}
}
