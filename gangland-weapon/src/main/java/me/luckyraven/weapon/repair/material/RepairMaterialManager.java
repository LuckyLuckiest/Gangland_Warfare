package me.luckyraven.weapon.repair.material;

import lombok.CustomLog;
import me.luckyraven.weapon.repair.config.RepairConfig;
import me.luckyraven.weapon.repair.config.RepairMaterialData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@CustomLog
public class RepairMaterialManager {

	private final Map<String, RepairMaterial> materials = new HashMap<>();

	public void load(@NotNull RepairConfig config) {
		materials.clear();
		for (Map.Entry<String, RepairMaterialData> entry : config.getRepairMaterials().entrySet()) {
			materials.put(entry.getKey(), new RepairMaterial(entry.getValue()));
		}
		log.info("Loaded {} repair material types", materials.size());
	}

	@Nullable
	public RepairMaterial getMaterial(@NotNull String id) {
		return materials.get(id);
	}

	@Nullable
	public RepairMaterial getMaterial(@Nullable ItemStack item) {
		String id = RepairMaterial.getMaterialId(item);
		if (id == null) return null;
		return getMaterial(id);
	}

	@NotNull
	public Map<String, RepairMaterial> getAllMaterials() {
		return new HashMap<>(materials);
	}
}
