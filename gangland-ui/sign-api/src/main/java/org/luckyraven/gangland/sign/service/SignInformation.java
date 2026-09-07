package org.luckyraven.gangland.sign.service;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.sign.bulk.BulkActionPreview;

public interface SignInformation {

	void sendSuccess(Player player, String message);

	void sendError(Player player, String message);

	String getMoneySymbol();

	String getSignCreated();

	String getSignCreationFailed(String reason);

	String getInvalidSign();

	/** Shown when a player may not create or break a plugin sign (docket LS-19). */
	String getSignNoPermission();

	String getBulkConfirmExpired();

	String getBulkConfirmRequest(BulkActionPreview preview, int confirmWindowSeconds);

	String getBulkExpired(String contentName);

	String getBulkCancelled(String contentName);

}
