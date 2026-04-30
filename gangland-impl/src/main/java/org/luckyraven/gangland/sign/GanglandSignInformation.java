package org.luckyraven.gangland.sign;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.bulk.BulkActionPreview;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.util.GanglandChatUtil;

public class GanglandSignInformation implements SignInformation {

	@Override
	public void sendSuccess(Player player, String message) {
		player.sendMessage(GanglandChatUtil.prefixMessage(message));
	}

	@Override
	public void sendError(Player player, String message) {
		player.sendMessage(GanglandChatUtil.errorMessage(message));
	}

	@Override
	public String getMoneySymbol() {
		return Settings.getMoneySymbol();
	}

	@Override
	public String getSignCreated() {
		return Messages.SIGN_CREATED.toString();
	}

	@Override
	public String getSignCreationFailed(String reason) {
		return Messages.SIGN_CREATION_FAILED.toString().replace("%reason%", reason);
	}

	@Override
	public String getInvalidSign() {
		return Messages.SIGN_INVALID.toString();
	}

	@Override
	public String getBulkConfirmExpired() {
		return Messages.SIGN_BULK_CONFIRM_EXPIRED.toString();
	}

	@Override
	public String getBulkConfirmRequest(BulkActionPreview preview, int confirmWindowSeconds) {
		return Messages.SIGN_BULK_CONFIRM_REQUEST.toString()
		                                         .replace("%quantity%", String.valueOf(preview.getQuantity()))
		                                         .replace("%content%", preview.getContentName())
		                                         .replace("%price%", String.format("%.2f", preview.getTotalPrice()))
		                                         .replace("%money_symbol%", Settings.getMoneySymbol())
		                                         .replace("%time%", String.valueOf(confirmWindowSeconds));
	}

	@Override
	public String getBulkExpired(String contentName) {
		return Messages.SIGN_BULK_EXPIRED.toString().replace("%content%", contentName);
	}

	@Override
	public String getBulkCancelled(String contentName) {
		return Messages.SIGN_BULK_CANCELLED.toString().replace("%content%", contentName);
	}
}
