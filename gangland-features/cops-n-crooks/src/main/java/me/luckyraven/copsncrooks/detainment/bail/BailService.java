package me.luckyraven.copsncrooks.detainment.bail;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentCostsContract;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentEconomyContract;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentEconomyContract.ChargeResult;
import me.luckyraven.copsncrooks.detainment.release.ReleasePipeline;
import me.luckyraven.copsncrooks.detainment.release.ReleaseReason;
import me.luckyraven.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.bukkit.entity.Player;

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
