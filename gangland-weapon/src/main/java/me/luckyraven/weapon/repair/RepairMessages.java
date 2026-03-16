package me.luckyraven.weapon.repair;

import org.jetbrains.annotations.NotNull;

public interface RepairMessages {

	@NotNull
	String alreadyFullyRepaired();

	@NotNull
	String repairComplete(int restored, int current, int max);

	@NotNull
	String incompatibleMaterial();

	@NotNull
	String repairCancelled();

	@NotNull
	String noMaterialAvailable();
}
