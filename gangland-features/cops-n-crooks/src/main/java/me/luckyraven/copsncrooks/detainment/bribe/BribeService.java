package me.luckyraven.copsncrooks.detainment.bribe;

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
import me.luckyraven.copsncrooks.detainment.wanted.WantedClearContract;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.copsncrooks.npc.police.state.CopState;
import me.luckyraven.copsncrooks.npc.police.state.CuffLockRegistry;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles both bribe paths:
 * <ul>
 *   <li>{@link #tryHandcuffBribe} — while HANDCUFFED, right-click the cuffing cop and pay to walk free.</li>
 *   <li>{@link #tryJailBribe} — from the jail paperwork GUI; cheaper but rolls {@code Success_Chance}. On failure the
 *       money is still consumed and the sentence is extended.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class BribeService {

	private final DetainmentService         detainmentService;
	private final DetainmentRegistry        detainmentRegistry;
	private final DetainmentCostsContract   costs;
	private final DetainmentEconomyContract economy;
	private final WantedClearContract       wantedClearContract;
	private final ReleasePipeline           releasePipeline;
	private final CuffLockRegistry          cuffLockRegistry;
	private final DetainmentSoundContract   sounds;

	// ── Handcuff bribe ───────────────────────────────────────────────────────

	public double computeHandcuffBribeCost(Player player) {
		int wantedLevel = wantedClearContract.getWantedLevel(player.getUniqueId());
		return costs.computeHandcuffBribeCost(wantedLevel);
	}

	public BribeResult tryHandcuffBribe(Player player, CopNpc cop) {
		if (!detainmentService.isHandcuffed(player)) return BribeResult.NOT_HANDCUFFED;

		UUID copId    = cop.getNpc().getUniqueId();
		UUID playerId = player.getUniqueId();

		if (!cuffLockRegistry.isOwner(playerId, copId)) return BribeResult.WRONG_COP;

		double cost = computeHandcuffBribeCost(player);

		ChargeResult result = economy.tryCharge(player, cost);
		switch (result) {
			case INSUFFICIENT_FUNDS:
				return BribeResult.INSUFFICIENT_FUNDS;
			case ECONOMY_ERROR:
				return BribeResult.ECONOMY_ERROR;
			case SUCCESS:
			default:
				break;
		}

		wantedClearContract.clearWanted(playerId);
		sounds.playBribeSuccess(player);
		releasePipeline.release(player, ReleaseReason.HANDCUFF_BRIBE);
		cop.transitionTo(CopState.RETURNING);
		return BribeResult.SUCCESS;
	}

	// ── Jail bribe ───────────────────────────────────────────────────────────

	public double computeJailBribeCost(Player player) {
		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		int wanted = detained == null || detained.getWantedAtArrest() == null ?
		             0 :
		             detained.getWantedAtArrest();
		return costs.computeJailBribeCost(wanted);
	}

	public BribeResult tryJailBribe(Player player) {
		if (!detainmentService.isJailed(player)) return BribeResult.NOT_JAILED;

		double cost = computeJailBribeCost(player);

		ChargeResult result = economy.tryCharge(player, cost);
		switch (result) {
			case INSUFFICIENT_FUNDS:
				return BribeResult.INSUFFICIENT_FUNDS;
			case ECONOMY_ERROR:
				return BribeResult.ECONOMY_ERROR;
			case SUCCESS:
			default:
				break;
		}

		double roll = ThreadLocalRandom.current().nextDouble();
		if (roll > costs.getJailBribeSuccessChance()) {
			extendSentence(player);
			sounds.playBribeFail(player);
			return BribeResult.FAIL;
		}

		sounds.playBribeSuccess(player);
		releasePipeline.release(player, ReleaseReason.JAIL_BRIBE);
		return BribeResult.SUCCESS;
	}

	private void extendSentence(Player player) {
		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		if (detained == null) return;

		long penaltyMs = costs.getJailBribeFailPenaltySeconds() * 1000L;
		long currentExpiry = detained.getSentenceExpiresAt() == null
		                     ? System.currentTimeMillis()
		                     : detained.getSentenceExpiresAt();
		long newExpiry = Math.max(System.currentTimeMillis(), currentExpiry) + penaltyMs;
		detained.setSentenceExpiresAt(newExpiry);
		detainmentRegistry.save(detained);
	}
}
