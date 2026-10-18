package org.luckyraven.gangland.copsncrooks.detainment.paperwork;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.detainment.bail.BailService;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.sentence.SentenceService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.keystone.inventory.InventoryService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lane bag, P-BAG-6: the jail paperwork's info lore gains the seized-bag line while one exists, after the fine line and
 * without disturbing a view with no bag installed.
 */
@DisplayName("PaperworkView - bag line")
class PaperworkViewBagTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000b46b");

	private PaperworkView view;

	@BeforeEach
	void setUp() {
		DetainmentMessageContract messages = mock(DetainmentMessageContract.class);
		when(messages.paperworkInfoLore(anyInt(), anyLong(), anyString())).thenReturn(List.of("base"));

		view = new PaperworkView(mock(JavaPlugin.class), mock(InventoryService.class), mock(DetainmentRegistry.class),
		                         mock(DetainmentCostsContract.class), mock(DetainmentEconomyContract.class),
		                         mock(BailService.class), mock(BribeService.class), mock(SentenceService.class),
		                         mock(MoneyIconProvider.class), messages, mock(WantedMessages.class));
	}

	private List<String> lore(DetainedPlayer detained) {
		return view.infoLore(ID, detained, 2, 120L, 50.0);
	}

	@Test
	@DisplayName("no bag line installed: the lore is exactly today's")
	void noInstallNoLine() {
		assertEquals(List.of("base"), lore(null));
	}

	@Test
	@DisplayName("an installed line source with a seized bag adds its text")
	void seizedBagAddsLine() {
		view.setBagLine(id -> id.equals(ID) ? "Bag seized: $800.00" : null);

		assertEquals(List.of("base", "Bag seized: $800.00"), lore(null));
	}

	@Test
	@DisplayName("a null line (nothing seized) adds nothing")
	void nullLineAddsNothing() {
		view.setBagLine(id -> null);

		assertEquals(List.of("base"), lore(new DetainedPlayer(ID, 1, DetainmentState.JAILED)));
	}

	@Test
	@DisplayName("the line comes for the viewing player's id only")
	void lineIsPerPlayer() {
		view.setBagLine(id -> id.equals(UUID.randomUUID()) ? "other" : null);

		assertEquals(List.of("base"), lore(null));
	}
}
