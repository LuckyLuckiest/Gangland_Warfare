package org.luckyraven.gangland.core.money;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.util.Placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A money formula never throws and never returns a negative: broken input falls back, and warns once per text.
 */
@DisplayName("MoneyFormula")
class MoneyFormulaTest {

	private final List<String> warnings = new ArrayList<>();
	private Consumer<String>   savedSink;

	@BeforeEach
	void setUp() {
		MoneyFormula.resetWarnings();
		savedSink = MoneyFormula.warnSink;
		MoneyFormula.warnSink = warnings::add;
	}

	@AfterEach
	void tearDown() {
		MoneyFormula.warnSink = savedSink;
		MoneyFormula.resetWarnings();
	}

	@Test
	@DisplayName("a valid formula is evaluated")
	void validFormula_evaluates() {
		assertEquals(150.0, MoneyFormula.evaluate("balance * 0.15", Map.of("balance", 1000.0), 7), 1e-9);
		assertTrue(warnings.isEmpty());
	}

	@Test
	@DisplayName("bad syntax returns the fallback")
	void badSyntax_returnsFallback() {
		assertEquals(7.0, MoneyFormula.evaluate("balance *", Map.of("balance", 1000.0), 7));
	}

	@Test
	@DisplayName("an unknown variable returns the fallback")
	void unknownVariable_returnsFallback() {
		assertEquals(7.0, MoneyFormula.evaluate("cash * 2", Map.of("balance", 1000.0), 7));
	}

	@Test
	@DisplayName("division by zero returns the fallback")
	void divisionByZero_returnsFallback() {
		assertEquals(7.0, MoneyFormula.evaluate("balance / 0", Map.of("balance", 1000.0), 7));
	}

	@Test
	@DisplayName("a negative result returns the fallback")
	void negativeResult_returnsFallback() {
		assertEquals(7.0, MoneyFormula.evaluate("0 - balance", Map.of("balance", 1000.0), 7));
	}

	@Test
	@DisplayName("a NaN result returns the fallback")
	void nanResult_returnsFallback() {
		assertEquals(7.0, MoneyFormula.evaluate("ln(0 - 1)", Map.of("balance", 1000.0), 7));
	}

	@Test
	@DisplayName("a negative fallback is clamped to zero")
	void negativeFallback_returnsZero() {
		assertEquals(0.0, MoneyFormula.evaluate("balance *", Map.of("balance", 1000.0), -5));
	}

	@Test
	@DisplayName("the same broken formula warns once, a different one warns again")
	void warning_loggedOncePerFormulaText() {
		MoneyFormula.evaluate("balance *", Map.of(), 1);
		MoneyFormula.evaluate("balance *", Map.of(), 1);
		assertEquals(1, warnings.size());

		MoneyFormula.evaluate("cash * 2", Map.of(), 1);
		assertEquals(2, warnings.size());
	}

	@Test
	@DisplayName("the shipped star-drop formula evaluates without a warning")
	void starDropDefault_evaluatesWithoutWarning() {
		double price = MoneyFormula.evaluate("amount * multiplier ^ wanted",
		                                     Map.of("amount", 50.0, "multiplier", 5.0, "wanted", 2.0), 0);

		assertEquals(1250.0, price, 1e-9);
		assertTrue(warnings.isEmpty());
	}

	@Test
	@DisplayName("userVariables carries the death-formula variable set")
	void userVariables_matchTheDeathFormulaSet() {
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		User<Player> user = new User<>(mock(JavaPlugin.class), player, mock(Placeholder.class));
		user.getEconomy().setAmount(Currency.of(300));
		user.getLevel().setLevelValue(4);
		user.getBounty().addBounty(mock(CommandSender.class), Currency.of(80));
		user.getWanted().setLevel(3);

		Map<String, Double> vars = MoneyFormula.userVariables(user);

		assertEquals(Map.of("balance", 300.0, "level", 4.0, "experience", 0.0, "bounty", 80.0, "wanted", 3.0), vars);
	}

}
