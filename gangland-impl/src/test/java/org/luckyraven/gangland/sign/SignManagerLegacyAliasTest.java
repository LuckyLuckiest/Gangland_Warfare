package org.luckyraven.gangland.sign;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.registry.SignFormatRegistry;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.service.SignFormatterService;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteraction;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializerRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * Pins T-G4b: {@link SignManager#setupSigns()} must redirect the six pre-0.9.0 weapon/ammo/wearable trade-sign
 * headers onto the generic {@code item-buy}/{@code item-sell} definitions at read time, prefixing the content line
 * with the alias namespace ({@link LegacySignRewriter}) so a sign placed before this stream keeps resolving once
 * Bartizan is installed. The physical sign block is never rewritten — only the parser fed to a placed sign's lines.
 */
@DisplayName("SignManager.setupSigns() — legacy weapon/ammo/wearable header redirection (T-G4b)")
class SignManagerLegacyAliasTest {

	@SuppressWarnings("unchecked")
	private SignManager buildManager() {
		DependencyContainer container = new DependencyContainer();
		SignFormatRegistry  formatRegistry  = new SignFormatRegistry();
		SignInteraction     signInteraction = new SignInteraction("glw", new SignTypeRegistry(),
		                                                         new SignFormatterService(formatRegistry),
		                                                         mock(SignInformation.class));

		UserManager<Player>        onlineUsers  = mock(UserManager.class);
		UserManager<OfflinePlayer> offlineUsers = mock(UserManager.class);

		LegacySignRewriter legacySignRewriter = new LegacySignRewriter("item-buy:weapon", "item-sell:weapon",
		                                                               "item-buy:ammo", "item-sell:ammo",
		                                                               "item-buy:wearable", "item-sell:wearable");

		return new SignManager(mock(Gangland.class), "glw", new SignTypeRegistry(), signInteraction,
		                       mock(UniqueItemAddon.class), mock(ItemSerializerRegistry.class),
		                       mock(ItemParser.class), onlineUsers, offlineUsers, container, legacySignRewriter);
	}

	@Test
	@DisplayName("a legacy weapon-buy header resolves through item-buy with a weapon: prefixed content")
	void legacyWeaponBuyHeader_resolvesToItemBuyWithWeaponPrefix() throws Exception {
		List<SignTypeDefinition> definitions = buildManager().setupSigns();

		SignTypeDefinition legacy = definitions.stream()
		                                       .filter(d -> "glw-weapon-buy".equals(d.getSignType().typed()))
		                                       .findFirst()
		                                       .orElseGet(() -> fail("glw-weapon-buy must be registered"));

		String[]   lines  = {"glw-weapon-buy", "AK47", "10", "1"};
		ParsedSign parsed = legacy.getSignParser().parse(lines, mock(Location.class));

		assertEquals("weapon:AK47", parsed.getContent());
	}

	@Test
	@DisplayName("a legacy wearable-sell header resolves through item-sell with a wearable: prefixed content")
	void legacyWearableSellHeader_resolvesToItemSellWithWearablePrefix() throws Exception {
		List<SignTypeDefinition> definitions = buildManager().setupSigns();

		SignTypeDefinition legacy = definitions.stream()
		                                       .filter(d -> "glw-wearable-sell".equals(d.getSignType().typed()))
		                                       .findFirst()
		                                       .orElseGet(() -> fail("glw-wearable-sell must be registered"));

		String[]   lines  = {"glw-wearable-sell", "Kevlar_Vest", "25", "1"};
		ParsedSign parsed = legacy.getSignParser().parse(lines, mock(Location.class));

		assertEquals("wearable:Kevlar_Vest", parsed.getContent());
	}

	@Test
	@DisplayName("an unrecognised legacy header (e.g. car-buy) is never registered as an alias")
	void unknownHeader_notRegistered() throws Exception {
		List<SignTypeDefinition> definitions = buildManager().setupSigns();

		boolean anyCarAlias = definitions.stream().anyMatch(d -> "glw-car-buy".equals(d.getSignType().typed()));

		assertFalse(anyCarAlias, "car-buy was never one of the six legacy headers and must not appear");
	}

}
