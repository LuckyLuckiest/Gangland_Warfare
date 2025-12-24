package me.luckyraven.sign;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.util.ChatUtil;
import org.bukkit.entity.Player;

public class GanglandSignInformation implements SignInformation {

	@Override
	public void sendSuccess(Player player, String message) {
		player.sendMessage(ChatUtil.prefixMessage(message));
	}

	@Override
	public void sendError(Player player, String message) {
		player.sendMessage(ChatUtil.errorMessage(message));
	}

	@Override
	public String getMoneySymbol() {
		return SettingAddon.getMoneySymbol();
	}
}
