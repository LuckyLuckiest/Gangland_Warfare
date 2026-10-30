package org.luckyraven.gangland.data.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.exception.EconomyException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Paying off your own bounty (0.21.4, owner ruling 2026-10-29, RDR2-style): the whole bounty, posters' escrow plus
 * notoriety, is withdrawn from the target's wallet and goes to "the city" (deposited nowhere, nobody is refunded),
 * the bounty is cleared and every online poster is told their stake went to the city. Shared by
 * {@code /glw bounty pay} and the [BOUNTY] sign (contents {@code pay} and legacy {@code clear}).
 * Messages the posters itself; the payer's line is the caller's, because the command and the sign prefix differently.
 */
public final class BountyPayoff {

	public enum Outcome {
		PAID,
		NO_BOUNTY,
		NO_MONEY
	}

	public record Result(Outcome outcome, BigDecimal amount) {

		public boolean paid() {
			return outcome == Outcome.PAID;
		}

		/** The payer's line with its own Commands/Errors prefix: for a command sender. */
		public String text() {
			return fill(message().toString());
		}

		/** The payer's line without a prefix: for a sign, whose SignInformation adds the prefix itself. */
		public String rawText() {
			return fill(message().toString(Messages.Type.NO_CHANGE));
		}

		private Messages message() {
			return switch (outcome) {
				case PAID -> Messages.BOUNTY_PAID;
				case NO_BOUNTY -> Messages.NO_BOUNTY_SELF;
				case NO_MONEY -> Messages.BOUNTY_PAY_NO_MONEY;
			};
		}

		private String fill(String text) {
			return text.replace("%amount%", Settings.formatAmount(amount));
		}
	}

	private BountyPayoff() {
	}

	public static Result pay(User<Player> user) {
		// Still loading (CreateAccountListener:69): the load would later overwrite the wallet and re-add the saved
		// bounty (UserDataLoader:141,196-197), and autosave/quit-save skip the user, so a pay-off now would take real
		// (Vault) money for an in-memory-only reset. Refuse as NO_BOUNTY; no new key.
		if (user.isLoadPending()) return new Result(Outcome.NO_BOUNTY, Currency.ZERO);

		Bounty bounty = user.getBounty();

		if (!bounty.hasBounty()) return new Result(Outcome.NO_BOUNTY, Currency.ZERO);

		BigDecimal amount = bounty.getAmount();

		// pre-check (ContactCommand / BountySetCommand pattern); the catch is the race backstop. Never surface the
		// EconomyException text ("Amount exceeding balance"): Argument.execute would forward it to the player.
		if (user.getEconomy().getAmount().compareTo(amount) < 0) return new Result(Outcome.NO_MONEY, amount);

		try {
			user.getEconomy().withdrawAmount(amount);
		} catch (EconomyException exception) {
			return new Result(Outcome.NO_MONEY, amount);
		}

		// The money goes to the city: deposited nowhere, by design (WB-22 ruling). Snapshot the stakes BEFORE the
		// reset, which clears both ledgers and stops the bounty timer.
		Map<String, BigDecimal> stakes = bounty.stakes();
		bounty.resetBounty();

		Player payer = user.getUser();
		stakes.forEach((id, stake) -> notifyPoster(id, stake, payer));

		return new Result(Outcome.PAID, amount);
	}

	private static void notifyPoster(String id, BigDecimal stake, Player payer) {
		Player poster = online(id);

		// console, legacy, a sender name, an offline poster or the payer themselves: no notice (accepted by the ruling)
		if (poster == null || poster.getUniqueId().equals(payer.getUniqueId())) return;

		poster.sendMessage(Messages.BOUNTY_PAID_NOTICE.toString()
		                                              .replace("%player%", payer.getName())
		                                              .replace("%amount%", Settings.formatAmount(stake)));
	}

	private static Player online(String id) {
		try {
			return Bukkit.getPlayer(UUID.fromString(id));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
