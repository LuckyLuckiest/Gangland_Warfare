package me.luckyraven.copsncrooks.npc.trader.config;

import me.luckyraven.shop.config.ShopUiSettings;

public interface TraderSettings extends ShopUiSettings {

	int getRespawnCooldownSeconds();

	int getHeadTrackRadius();

	String getFallbackTraitId();

	int getSellMaxOfferSlots();

	double getMoodPerSale();

	double getTipAmount();

}
