package me.luckyraven.file.configuration.weapon;

import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.repair.RepairMessages;
import me.luckyraven.util.GanglandChatUtil;
import org.jetbrains.annotations.NotNull;

public class GanglandRepairMessages implements RepairMessages {

	@Override
	@NotNull
	public String getAlreadyFullyRepaired() {
		return Messages.REPAIR_ALREADY_FULL.toString();
	}

	@Override
	@NotNull
	public String getRepairComplete(int restored, int current, int max) {
		return GanglandChatUtil.color(Messages.REPAIR_COMPLETE.toString()
		                                                      .replace("%restored%", String.valueOf(restored))
		                                                      .replace("%current%", String.valueOf(current))
		                                                      .replace("%max%", String.valueOf(max)));
	}

	@Override
	@NotNull
	public String getIncompatibleMaterial() {
		return Messages.REPAIR_INCOMPATIBLE_MATERIAL.toString();
	}

	@Override
	@NotNull
	public String getRepairCancelled() {
		return Messages.REPAIR_CANCELLED.toString();
	}

	@Override
	@NotNull
	public String getNoMaterialAvailable() {
		return Messages.REPAIR_NO_MATERIAL.toString();
	}
}
