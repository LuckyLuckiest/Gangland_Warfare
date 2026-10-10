package org.luckyraven.gangland.turf.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.math.BigDecimal;

/**
 * D8 = B claim: when a gang takes a turf from a gang with a bounty on it, the whole bounty moves into the capturing
 * gang's bank and the bounty resets. Money is moved, never minted. The whole pot moves only when it fits under the
 * capturing gang's {@code Maximum_Balance} (T-259); otherwise nothing moves and the bounty stays on the bountied gang. An unclaimed turf, an ally, the same gang, or a
 * previous owner with no bounty moves nothing.
 */
@ListenerHandler
public final class TurfGangBountyListener implements Listener {

	private final UserLookupContract users;
	private final GangMessages       messages;

	public TurfGangBountyListener(UserLookupContract users, GangMessages messages) {
		this.users    = users;
		this.messages = messages;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCaptured(TurfCapturedEvent event) {
		Gang bountied = event.getOldOwner();
		Gang capturer = event.getNewOwner();

		if (bountied == null || capturer == null) {
			return;
		}
		if (bountied.getId() == capturer.getId() || capturer.isAlly(bountied)) {
			return;
		}

		Bounty bounty = bountied.getBounty();
		if (!bounty.hasBounty()) {
			return;
		}

		BigDecimal pot    = bounty.getAmount();
		String     turf   = event.getTurf().getDisplayName();
		String     amount = Settings.formatAmount(pot);
		String     symbol = Settings.getMoneySymbol();

		// T-259: the pot moves only if it fits under the gang bank cap; otherwise it stays on the bountied gang
		if (capturer.getEconomy().getAmount().add(pot).compareTo(GangSettings.getGangMaxBalance()) > 0) {
			for (var member : capturer.getOnlineMembers(users::findByPlayer)) {
				member.getUser().sendMessage(messages.text("Bounty.Bank_Full",
				                                           "&eYour gang bank cannot hold the %money_symbol%%amount% bounty on %gang%; it stays on them.",
				                                           "money_symbol", symbol, "amount", amount,
				                                           "gang", bountied.getDisplayNameString()));
			}
			return;
		}

		capturer.getEconomy().depositAmount(pot);
		bounty.resetBounty();

		for (var member : bountied.getOnlineMembers(users::findByPlayer)) {
			member.getUser().sendMessage(messages.text("Bounty.Lost",
			                                           "&c%gang% took %turf% and collected the %money_symbol%%amount% bounty on your gang.",
			                                           "gang", capturer.getDisplayNameString(), "turf", turf,
			                                           "money_symbol", symbol, "amount", amount));
		}
		for (var member : capturer.getOnlineMembers(users::findByPlayer)) {
			member.getUser().sendMessage(messages.text("Bounty.Claimed",
			                                           "&aYour gang took %turf% from %gang% and collected their %money_symbol%%amount% bounty.",
			                                           "turf", turf, "gang", bountied.getDisplayNameString(),
			                                           "money_symbol", symbol, "amount", amount));
		}
	}

}
