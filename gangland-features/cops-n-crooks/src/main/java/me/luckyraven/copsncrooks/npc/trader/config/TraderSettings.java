package me.luckyraven.copsncrooks.npc.trader.config;

import me.luckyraven.shop.config.ShopUiSettings;

public interface TraderSettings extends ShopUiSettings {

	int getRespawnCooldownSeconds();

	int getHeadTrackRadius();

	double getMoodDecayPerSecondFloor();

	String getFallbackTraitId();

}
