package org.luckyraven.gangland.npcshops.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.message.MessageProvider;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises how {@link GanglandBankerMessages} fills the money symbol and one amount token into a template, before
 * the five hand-rolled replace chains share the {@code fillAmount} helper. Every template carries all tokens, so the
 * assertions also pin which tokens are replaced and which are left alone.
 */
@DisplayName("GanglandBankerMessages - amount templates")
class GanglandBankerMessagesTest {

	private static final String ALL_TOKENS = "<%money_symbol%|%amount%|%limit%|%cap%|%cost%|%fee%>";

	private final GanglandBankerMessages messages = new GanglandBankerMessages();

	@BeforeAll
	static void primeStatics() throws ReflectiveOperationException {
		// Settings' fields are process-wide statics (documentation/TESTING.md section 4); an unset Money_Symbol NPEs.
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return ALL_TOKENS;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
	}

	private static String filled(Messages template, String token, BigDecimal value) {
		String raw = template.toString();
		return raw.replace("%money_symbol%", "$").replace(token, Settings.formatAmount(value));
	}

	private void assertFillsOnly(Messages template, String token, Function<BigDecimal, String> message) {
		BigDecimal value = new BigDecimal("1234.5");

		String actual = message.apply(value);

		assertEquals(filled(template, token, value), actual);
		assertTrue(actual.contains(Settings.formatAmount(value)), actual);
		assertTrue(!actual.contains("%money_symbol%") && !actual.contains(token), actual);
		for (String other : List.of("%amount%", "%limit%", "%cap%", "%cost%", "%fee%")) {
			if (!other.equals(token)) assertTrue(actual.contains(other), other + " must be left untouched: " + actual);
		}
	}

	@Test
	@DisplayName("the %amount% messages fill the money symbol and the amount")
	void amountMessages() {
		assertFillsOnly(Messages.BANKER_DEPOSIT_SUCCESS, "%amount%", messages::depositSuccess);
		assertFillsOnly(Messages.BANKER_WITHDRAW_SUCCESS, "%amount%", messages::withdrawSuccess);
		assertFillsOnly(Messages.BANKER_INSUFFICIENT_CASH, "%amount%", messages::insufficientCash);
		assertFillsOnly(Messages.BANKER_INSUFFICIENT_BANK_FUNDS, "%amount%", messages::insufficientBankFunds);
		assertFillsOnly(Messages.BANKER_LOAN_WEEKLY_SUCCESS, "%amount%", messages::weeklyLoanSuccess);
		assertFillsOnly(Messages.BANKER_LOAN_MONTHLY_SUCCESS, "%amount%", messages::monthlyLoanSuccess);
		assertFillsOnly(Messages.BANKER_LOAN_CAP_FULL, "%amount%", messages::loanCapFull);
	}

	@Test
	@DisplayName("dailyDepositReached fills %limit%")
	void dailyDepositReached() {
		assertFillsOnly(Messages.BANKER_DAILY_DEPOSIT_REACHED, "%limit%", messages::dailyDepositReached);
	}

	@Test
	@DisplayName("capExceeded fills %cap%")
	void capExceeded() {
		assertFillsOnly(Messages.BANKER_CAP_EXCEEDED, "%cap%", messages::capExceeded);
	}

	@Test
	@DisplayName("upgradeInsufficientFunds fills %cost%")
	void upgradeInsufficientFunds() {
		assertFillsOnly(Messages.BANKER_UPGRADE_INSUFFICIENT_FUNDS, "%cost%", messages::upgradeInsufficientFunds);
	}

	@Test
	@DisplayName("createCannotAfford and renameCannotAfford fill %fee%")
	void feeMessages() {
		assertFillsOnly(Messages.BANKER_CREATE_CANNOT_AFFORD, "%fee%", messages::createCannotAfford);
		assertFillsOnly(Messages.BANKER_RENAME_CANNOT_AFFORD, "%fee%", messages::renameCannotAfford);
	}
}
