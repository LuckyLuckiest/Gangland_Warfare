package me.luckyraven.sign.service;

import org.bukkit.entity.Player;

public interface SignInformation {

	void sendSuccess(Player player, String message);

	void sendError(Player player, String message);

	String getMoneySymbol();

}
