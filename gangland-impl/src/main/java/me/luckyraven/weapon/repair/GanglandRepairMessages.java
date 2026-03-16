package me.luckyraven.weapon.repair;

import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.jetbrains.annotations.NotNull;

public class GanglandRepairMessages implements RepairMessages {

	@Override
	public @NotNull String alreadyFullyRepaired() {
		return MessageAddon.REPAIR_ALREADY_FULL.toString();
	}

	@Override
	public @NotNull String repairComplete(int restored, int current, int max) {
		return ChatUtil.color(MessageAddon.REPAIR_COMPLETE.toString()
														  .replace("%restored%", String.valueOf(restored))
														  .replace("%current%", String.valueOf(current))
														  .replace("%max%", String.valueOf(max)));
	}

	@Override
	public @NotNull String incompatibleMaterial() {
		return MessageAddon.REPAIR_INCOMPATIBLE_MATERIAL.toString();
	}

	@Override
	public @NotNull String repairCancelled() {
		return MessageAddon.REPAIR_CANCELLED.toString();
	}

	@Override
	public @NotNull String noMaterialAvailable() {
		return MessageAddon.REPAIR_NO_MATERIAL.toString();
	}
}
