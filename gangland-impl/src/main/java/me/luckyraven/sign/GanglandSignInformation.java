package me.luckyraven.sign;

import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.bulk.BulkActionPreview;
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

	@Override
	public String getSignCreated() {
		return MessageAddon.SIGN_CREATED.toString();
	}

	@Override
	public String getSignCreationFailed(String reason) {
		return MessageAddon.SIGN_CREATION_FAILED.toString().replace("%reason%", reason);
	}

	@Override
	public String getInvalidSign() {
		return MessageAddon.SIGN_INVALID.toString();
	}

	@Override
	public String getBulkConfirmExpired() {
		return MessageAddon.SIGN_BULK_CONFIRM_EXPIRED.toString();
	}

	@Override
	public String getBulkConfirmRequest(BulkActionPreview preview, int confirmWindowSeconds) {
		return MessageAddon.SIGN_BULK_CONFIRM_REQUEST.toString()
													 .replace("%quantity%", String.valueOf(preview.getQuantity()))
													 .replace("%content%", preview.getContentName())
													 .replace("%price%", String.format("%.2f", preview.getTotalPrice()))
													 .replace("%money_symbol%", SettingAddon.getMoneySymbol())
													 .replace("%time%", String.valueOf(confirmWindowSeconds));
	}

	@Override
	public String getBulkExpired(String contentName) {
		return MessageAddon.SIGN_BULK_EXPIRED.toString().replace("%content%", contentName);
	}

	@Override
	public String getBulkCancelled(String contentName) {
		return MessageAddon.SIGN_BULK_CANCELLED.toString().replace("%content%", contentName);
	}
}
