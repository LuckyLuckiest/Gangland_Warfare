package me.luckyraven.copsncrooks.npc.trader.config;

import me.luckyraven.shop.config.ShopUiSettings;

import java.math.BigDecimal;

public interface TraderSettings extends ShopUiSettings {

	int getRespawnCooldownSeconds();

	int getHeadTrackRadius();

	String getFallbackTraitId();

	int getSellMaxOfferSlots();

	double getMoodPerSale();

	BigDecimal getTipAmount();

}
