package me.luckyraven.weapon.repair;

import org.jetbrains.annotations.NotNull;

public interface RepairMessages {

	@NotNull
	String getAlreadyFullyRepaired();

	@NotNull
	String getRepairComplete(int restored, int current, int max);

	@NotNull
	String getIncompatibleMaterial();

	@NotNull
	String getRepairCancelled();

	@NotNull
	String getNoMaterialAvailable();
}
