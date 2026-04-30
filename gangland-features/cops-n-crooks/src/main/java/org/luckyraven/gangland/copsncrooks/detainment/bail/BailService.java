package org.luckyraven.gangland.copsncrooks.detainment.bail;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract.ChargeResult;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;

/**
 * Bail payment service. Charges the player's balance and routes successful releases through the {@link ReleasePipeline}
 * so seized items are restored.
 */
@RequiredArgsConstructor
public class BailService {

	private final DetainmentService         detainmentService;
	private final DetainmentRegistry        detainmentRegistry;
	private final DetainmentCostsContract   costs;
	private final DetainmentEconomyContract economy;
	private final ReleasePipeline           releasePipeline;
	private final DetainmentSoundContract   sounds;

	public double computeCost(Player player) {
		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		int wanted = detained == null || detained.getWantedAtArrest() == null ?
		             0 :
		             detained.getWantedAtArrest();
		return costs.computeBailCost(wanted);
	}

	public BailResult tryPayBail(Player player) {
		if (!detainmentService.isJailed(player)) return BailResult.NOT_JAILED;

		double cost = computeCost(player);

		ChargeResult result = economy.tryCharge(player, cost);
		switch (result) {
			case INSUFFICIENT_FUNDS:
				return BailResult.INSUFFICIENT_FUNDS;
			case ECONOMY_ERROR:
				return BailResult.ECONOMY_ERROR;
			case SUCCESS:
			default:
				break;
		}

		sounds.playBailSuccess(player);
		releasePipeline.release(player, ReleaseReason.BAIL);
		return BailResult.SUCCESS;
	}
}
