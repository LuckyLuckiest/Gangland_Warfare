package org.luckyraven.gangland.copsncrooks.npc.trader.config;

import org.luckyraven.gangland.shop.config.ShopUiSettings;

import java.math.BigDecimal;

public interface TraderSettings extends ShopUiSettings {

	int getRespawnCooldownSeconds();

	int getHeadTrackRadius();

	String getFallbackTraitId();

	int getSellMaxOfferSlots();

	double getMoodPerSale();

	BigDecimal getTipAmount();

}
