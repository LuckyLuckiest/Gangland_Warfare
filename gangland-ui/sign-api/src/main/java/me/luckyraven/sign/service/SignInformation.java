package me.luckyraven.sign.service;

import me.luckyraven.sign.bulk.BulkActionPreview;
import org.bukkit.entity.Player;

public interface SignInformation {

	void sendSuccess(Player player, String message);

	void sendError(Player player, String message);

	String getMoneySymbol();

	String getSignCreated();

	String getSignCreationFailed(String reason);

	String getInvalidSign();

	String getBulkConfirmExpired();

	String getBulkConfirmRequest(BulkActionPreview preview, int confirmWindowSeconds);

	String getBulkExpired(String contentName);

	String getBulkCancelled(String contentName);

}
