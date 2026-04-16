package me.luckyraven.copsncrooks.npc.trader.config;

import me.luckyraven.shop.config.ShopUiSettings;

import java.util.List;

public interface TraderSettings extends ShopUiSettings {

	int getRespawnCooldownSeconds();

	int getHeadTrackRadius();

	double getMoodDecayPerSecondFloor();

	String getFallbackTraitId();

	int getSellMaxOfferSlots();

	double getMoodPerSale();

	List<Double> getSellBargainMultipliers();

	int getBargainCooldownSeconds();

}
