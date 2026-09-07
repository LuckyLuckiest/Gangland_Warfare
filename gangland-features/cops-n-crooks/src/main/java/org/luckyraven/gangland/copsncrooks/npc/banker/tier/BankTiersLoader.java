package org.luckyraven.gangland.copsncrooks.npc.banker.tier;

import lombok.CustomLog;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@CustomLog
public final class BankTiersLoader implements BeanLifecycle {

	private final FileHandler      fileHandler;
	private final BankTierRegistry registry;

	public BankTiersLoader(BankTierRegistry registry, FileManager fileManager) {
		this.registry = registry;

		try {
			String fileName = "bank_tiers";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	/**
	 * Reads a currency-valued YAML key as {@link BigDecimal}. YAML numeric literals round-trip through the node reader
	 * as strings here so values like {@code 10_000_000_000_000_000} (far beyond {@code 2^53}) don't lose precision;
	 * missing / blank keys default to zero.
	 */
	private static BigDecimal parseCurrency(NodeReader reader, String key) {
		String raw = reader.get(key).asString().orDefault("0");
		return Currency.parse(raw);
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

		Map<String, BankTier> parsed = new LinkedHashMap<>();

		int autoOrder = 0;
		for (String id : reader.keys()) {
			if (id.equalsIgnoreCase("Config_Version")) continue;

			MappingNode entry = reader.get(id).asMapping().required().orNull();
			if (entry == null) continue;

			parsed.put(id, parseTier(id, NodeReader.of(entry, report), autoOrder++));
		}

		if (!report.isEmpty()) report.log(log);

		if (parsed.isEmpty()) {
			log.warn("Bank tiers parsed to zero tiers; keeping previous registry state");
			return;
		}

		registry.replaceAll(parsed);
		log.debug("Loaded {} bank tier(s): {}", parsed.size(), parsed.keySet());
	}

	private BankTier parseTier(String id, NodeReader r, int fallbackOrder) {
		String     displayName       = r.get("Display_Name").asString().orDefault(id);
		BigDecimal maxBalance        = parseCurrency(r, "Max_Balance");
		BigDecimal upgradeCost       = parseCurrency(r, "Upgrade_Cost");
		int        order             = r.get("Order").asInt().orDefault(fallbackOrder);
		BigDecimal dailyDepositLimit = parseCurrency(r, "Daily_Deposit_Limit");
		double     interestRate      = r.get("Interest_Rate").asDouble().min(0).orDefault(0.0);
		double     deathLossDiscount = r.get("Death_Loss_Discount").asDouble().min(0).max(1).orDefault(0.0);
		BigDecimal weeklyLoan        = parseCurrency(r, "Weekly_Loan_Amount");
		BigDecimal monthlyLoan       = parseCurrency(r, "Monthly_Loan_Amount");

		return new BankTier(id, displayName, maxBalance, upgradeCost, order,
		                    dailyDepositLimit, interestRate, deathLossDiscount, weeklyLoan, monthlyLoan);
	}

}
