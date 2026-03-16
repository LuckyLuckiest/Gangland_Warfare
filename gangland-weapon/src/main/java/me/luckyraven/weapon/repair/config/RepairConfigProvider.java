package me.luckyraven.weapon.repair.config;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface RepairConfigProvider {

	@NotNull
	Map<String, RepairMaterialData> getRepairMaterials();
}
