package me.luckyraven.copsncrooks.npc.trader.trait;

import lombok.CustomLog;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.FileHandlerReader;
import me.luckyraven.persistence.config.MappingNode;
import me.luckyraven.persistence.config.NodeReader;
import me.luckyraven.util.autowire.bean.BeanLifecycle;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@CustomLog
public final class TraderTraitsLoader implements BeanLifecycle {

	private final FileHandler         fileHandler;
	private final TraderTraitRegistry registry;

	public TraderTraitsLoader(TraderTraitRegistry registry, FileManager fileManager) {
		this.registry = registry;

		try {
			String fileName = "trader_traits";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		load();
	}

	@Override
	public void onClear() {
		registry.replaceAll(Collections.emptyMap());
	}

	public void load() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = FileHandlerReader.read(fileHandler, report);

		Map<String, TraderTraitDefinition> parsed = new LinkedHashMap<>();

		for (String id : reader.keys()) {
			if (id.equalsIgnoreCase("Config_Version")) continue;

			MappingNode entry = reader.get(id).asMapping().required().orNull();
			if (entry == null) continue;

			parsed.put(id, parseDefinition(id, NodeReader.of(entry, report)));
		}

		if (!report.isEmpty()) report.log(log);

		if (parsed.isEmpty()) {
			log.warn("Trader traits parsed to zero traits; keeping previous registry state");
			return;
		}

		registry.replaceAll(parsed);
		log.debug("Loaded {} trader trait(s): {}", parsed.size(), parsed.keySet());
	}

	private TraderTraitDefinition parseDefinition(String id, NodeReader r) {
		double sellPriceRatio   = r.get("Sell_Price_Ratio").asDouble().min(0).required().orDefault(0.0);
		double barterPriceRatio = r.get("Barter_Price_Ratio").asDouble().min(0).orDefault(sellPriceRatio);

		TraderTraitProfile profile = new TraderTraitProfile(
				r.get("Mood_Per_Tip_Currency").asDouble().required().orDefault(0.0),
				r.get("Mood_Per_Purchase").asDouble().required().orDefault(0.0),
				r.get("Min_Friend_Discount").asDouble().min(0).max(1).required().orDefault(0.0),
				r.get("Allows_Barter").asBool().required().orDefault(false),
				sellPriceRatio,
				barterPriceRatio,
				r.get("Max_Health").asDouble().min(0).orDefault(20.0),
				r.get("Invulnerable").asBool().orDefault(true)
		);

		String displayName = r.get("Display_Name").asString().orDefault(id);

		return new TraderTraitDefinition(id, displayName, profile);
	}

}
