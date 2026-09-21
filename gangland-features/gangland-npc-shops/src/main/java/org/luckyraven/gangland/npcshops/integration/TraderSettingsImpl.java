package org.luckyraven.gangland.npcshops.integration;

import lombok.CustomLog;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.npcshops.trader.config.TraderSettings;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reads {@code npc/trader_settings.yml} (module-owned default, WS4 G1a — moved out of the core settings.yml
 * {@code Trader:} block). {@link #getMaxModeMultiplier()}/{@link #getInventoryFillName()}/
 * {@link #getInventoryFillItem()} stay delegated to core {@link Settings} — {@code Max_Mode_Multiplier} is shared
 * with the admin price editor (settings.yml {@code Shop:} block) and the fill knobs are generic UI settings, not
 * trader-specific.
 */
@CustomLog
public final class TraderSettingsImpl implements TraderSettings, BeanLifecycle {

	private final FileHandler fileHandler;

	private volatile int        respawnCooldownSeconds;
	private volatile int        headTrackRadius;
	private volatile String     fallbackTraitId;
	private volatile int        sellMaxOfferSlots;
	private volatile double     moodPerSale;
	private volatile BigDecimal tipAmount = BigDecimal.valueOf(100);

	public TraderSettingsImpl(FileManager fileManager) {
		try {
			String fileName = "trader_settings";
			fileManager.checkFileLoaded(fileName);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException e) {
			throw new PluginException(e);
		}
		load();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		load();
	}

	private void load() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = FileHandlerReader.read(fileHandler, report);

		MappingNode sellNode = reader.get("Sell").asMapping().orNull();
		NodeReader  sell     = sellNode == null ? null : NodeReader.of(sellNode, report);

		respawnCooldownSeconds = reader.get("Respawn_Cooldown").asInt().orDefault(60);
		headTrackRadius        = reader.get("Head_Track_Radius").asInt().orDefault(8);
		fallbackTraitId        = reader.get("Fallback_Trait_Id").asString().orDefault("easygoing");
		sellMaxOfferSlots      = sell == null ? 20 : sell.get("Max_Offer_Slots").asInt().orDefault(20);
		moodPerSale            = sell == null ? 0.02 : sell.get("Mood_Per_Sale").asDouble().orDefault(0.02);

		String rawTip = reader.get("Tip_Amount").asString().orDefault("100");
		try {
			tipAmount = new BigDecimal(rawTip.trim());
		} catch (NumberFormatException e) {
			tipAmount = BigDecimal.valueOf(100);
		}

		if (!report.isEmpty()) report.log(log);
	}

	@Override
	public int getRespawnCooldownSeconds() {
		return respawnCooldownSeconds;
	}

	@Override
	public int getHeadTrackRadius() {
		return headTrackRadius;
	}

	@Override
	public String getFallbackTraitId() {
		return fallbackTraitId;
	}

	@Override
	public int getMaxModeMultiplier() {
		return Settings.getShopMaxModeMultiplier();
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
		return sellMaxOfferSlots;
	}

	@Override
	public double getMoodPerSale() {
		return moodPerSale;
	}

	@Override
	public BigDecimal getTipAmount() {
		return tipAmount;
	}

}
