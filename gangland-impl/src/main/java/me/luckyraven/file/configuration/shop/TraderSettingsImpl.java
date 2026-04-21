package me.luckyraven.file.configuration.shop;

import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.file.configuration.Settings;

import java.math.BigDecimal;

public class TraderSettingsImpl implements TraderSettings {

	@Override
	public int getRespawnCooldownSeconds() {
		return Settings.getTraderRespawnCooldownSeconds();
	}

	@Override
	public int getHeadTrackRadius() {
		return Settings.getTraderHeadTrackRadius();
	}

	@Override
	public String getFallbackTraitId() {
		return Settings.getTraderFallbackTraitId();
	}

	@Override
	public int getMaxModeMultiplier() {
		return Settings.getTraderMaxModeMultiplier();
	}

	@Override
	public String getInventoryFillName() {
		return Settings.getInventoryFillName();
	}

	@Override
	public String getInventoryFillItem() {
		return Settings.getInventoryFillItem();
	}

	@Override
	public int getSellMaxOfferSlots() {
		return Settings.getTraderSellMaxOfferSlots();
	}

	@Override
	public double getMoodPerSale() {
		return Settings.getTraderMoodPerSale();
	}

	@Override
	public BigDecimal getTipAmount() {
		return Settings.getTraderTipAmount();
	}

}
