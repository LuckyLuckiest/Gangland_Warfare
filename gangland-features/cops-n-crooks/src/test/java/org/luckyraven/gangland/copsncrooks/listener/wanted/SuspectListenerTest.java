package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFilter;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescription;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.WearableKeys;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P-DIS-1 / P-DIS-4: crimes snapshot the suspect description and blow the police outfit; a chase end forgets them. */
@DisplayName("SuspectListener - crime snapshots and chase end")
class SuspectListenerTest {

	private final UUID id = UUID.randomUUID();

	private Player              player;
	private ItemStack           helmet;
	private ItemStack           chest;
	private SuspectDescriptions descriptions;
	private PoliceOutfit        outfit;
	private SuspectListener     listener;
	private DisguiseTick        tick;
	private HeatLedger          ledger;
	private PostEscapeSearch    search;
	private DisguiseFilter      filter;

	@BeforeEach
	void setUp() {
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		helmet = mock(ItemStack.class);
		chest  = mock(ItemStack.class);
		when(helmet.getType()).thenReturn(Material.CARVED_PUMPKIN);
		when(chest.getType()).thenReturn(Material.LEATHER_CHESTPLATE);
		when(inventory.getHelmet()).thenAnswer(inv -> helmet);
		when(inventory.getChestplate()).thenAnswer(inv -> chest);
		org.bukkit.inventory.meta.LeatherArmorMeta meta = mock(org.bukkit.inventory.meta.LeatherArmorMeta.class);
		when(meta.getColor()).thenReturn(Color.RED);
		when(chest.getItemMeta()).thenReturn(meta);

		WantedMessages messages = mock(WantedMessages.class);
		when(messages.format(any(WantedMessages.Key.class), anyMap())).thenReturn("x");
		WearableKeys keys = item -> item == helmet ? "police_helmet" : item == chest ? "police_vest" : null;
		descriptions = new SuspectDescriptions(() -> DisguiseSettings.DEFAULT, keys, messages);
		outfit       = new PoliceOutfit(() -> DisguiseSettings.DEFAULT, keys);
		tick         = mock(DisguiseTick.class);
		ledger       = mock(HeatLedger.class);
		search       = mock(PostEscapeSearch.class);
		filter       = mock(DisguiseFilter.class);
		when(filter.hunted(player)).thenReturn(true);
		listener     = new SuspectListener(descriptions, outfit, tick, ledger, search, filter);
	}

	private CrimeCommittedEvent crime(boolean cancelled) {
		CrimeCommittedEvent event = new CrimeCommittedEvent(player, "Car_Theft", mock(Location.class), true, 0);
		event.setCancelled(cancelled);
		return event;
	}

	@Test
	@DisplayName("the listener is a @ListenerHandler")
	void isAListenerHandler() {
		assertTrue(SuspectListener.class.isAnnotationPresent(ListenerHandler.class));
	}

	/** Delivers {@code event} as Bukkit would: a cancelled crime skips a handler that ignores cancelled events. */
	private void fire(CrimeCommittedEvent event) throws NoSuchMethodException {
		EventHandler handler = SuspectListener.class.getMethod("onCrime", CrimeCommittedEvent.class)
		                                            .getAnnotation(EventHandler.class);
		if (event.isCancelled() && handler.ignoreCancelled()) return;

		listener.onCrimeBegins(event);
		listener.onCrime(event);
	}

	private WantedEndEvent end(WantedCause cause) {
		WantedEndEvent end = mock(WantedEndEvent.class);
		when(end.getPlayer()).thenReturn(player);
		when(end.getCause()).thenReturn(cause);
		return end;
	}

	@Test
	@DisplayName("0.21.5: onCrime runs at MONITOR and sees cancelled (witness-gated) crimes: the cancel is the crime")
	void crimeHandlerSeesCancelledCrimes() throws NoSuchMethodException {
		EventHandler handler = SuspectListener.class.getMethod("onCrime", CrimeCommittedEvent.class)
		                                            .getAnnotation(EventHandler.class);

		assertNotNull(handler);
		assertEquals(EventPriority.MONITOR, handler.priority());
		assertFalse(handler.ignoreCancelled(), "the gate's cancel is the moment of the crime");
	}

	@Test
	@DisplayName("0.21.5 (843): a gated crime's report records the look at the crime, not at the landing")
	void gatedCrimeKeepsTheLookAtTheCrime() throws NoSuchMethodException {
		fire(crime(true));
		helmet = null;
		when(ledger.isReporting(id)).thenReturn(true);

		fire(crime(false));

		SuspectDescription stored = descriptions.latest(id);
		assertTrue(stored.masked(), "the pumpkin was on at the crime");
		assertEquals("police helmet", stored.head());
	}

	@Test
	@DisplayName("0.21.5 (843): the outfit worn at a gated crime is blown when its report lands, even taken off since")
	void outfitWornAtTheCrimeBlowsItAtLanding() throws NoSuchMethodException {
		when(helmet.getType()).thenReturn(Material.IRON_HELMET);
		when(chest.getType()).thenReturn(Material.IRON_CHESTPLATE);
		ItemStack wornHelmet = helmet;
		ItemStack wornChest  = chest;
		fire(crime(true));
		helmet = null;
		chest  = null;
		when(ledger.isReporting(id)).thenReturn(true);

		fire(crime(false));
		helmet = wornHelmet;
		chest  = wornChest;

		assertFalse(outfit.hides(player, 1), "worn again, the outfit stays blown");
	}

	@Test
	@DisplayName("0.21.5: a held look is read only by a landing report; any other crime records the look now")
	void heldLookIsReadOnlyByALanding() throws NoSuchMethodException {
		fire(crime(true));
		helmet = null;

		fire(crime(false));

		assertFalse(descriptions.latest(id).masked());
	}

	@Test
	@DisplayName("0.21.5 (844): an escape keeps the description and the blown outfit for the post-escape search")
	void escapeKeepsTheDescription() {
		when(helmet.getType()).thenReturn(Material.IRON_HELMET);
		when(chest.getType()).thenReturn(Material.IRON_CHESTPLATE);
		listener.onCrime(crime(false));
		when(search.isEscape(WantedCause.EVASION)).thenReturn(true);

		listener.onChaseEnd(end(WantedCause.EVASION));

		assertNotNull(descriptions.latest(id));
		assertFalse(outfit.hides(player, 1), "still blown for the search");
		verify(tick, never()).forget(id);
	}

	@Test
	@DisplayName("0.21.5 (D14): a quit clears the description and the held looks")
	void quitClearsTheDescription() throws NoSuchMethodException {
		listener.onCrime(crime(false));
		fire(crime(true));

		listener.onQuit(quit());

		assertNull(descriptions.latest(id));
		helmet = null;
		when(ledger.isReporting(id)).thenReturn(true);
		fire(crime(false));
		assertFalse(descriptions.latest(id).masked(), "a report landing after the quit records the look now");
	}

	private org.bukkit.event.player.PlayerQuitEvent quit() {
		org.bukkit.event.player.PlayerQuitEvent quit = mock(org.bukkit.event.player.PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(player);
		return quit;
	}

	@Test
	@DisplayName("a crime stores the description of the player as they look right now")
	void crimeStoresDescription() {
		listener.onCrime(crime(false));

		SuspectDescription stored = descriptions.latest(id);
		assertNotNull(stored);
		assertTrue(stored.masked());
		assertEquals("police helmet", stored.head());
		assertEquals("police vest", stored.chest());
	}

	@Test
	@DisplayName("a later crime replaces the earlier description")
	void laterCrimeReplaces() {
		listener.onCrime(crime(false));
		helmet = null;

		listener.onCrime(crime(false));

		assertFalse(descriptions.latest(id).masked());
	}

	@Test
	@DisplayName("a crime in the police outfit blows it")
	void crimeInOutfitBlowsIt() {
		when(helmet.getType()).thenReturn(Material.IRON_HELMET);
		when(chest.getType()).thenReturn(Material.IRON_CHESTPLATE);
		assertTrue(outfit.hides(player, 1));

		listener.onCrime(crime(false));

		assertFalse(outfit.hides(player, 1));
	}

	@Test
	@DisplayName("chase end clears the description and the blown flag")
	void chaseEndClears() {
		when(helmet.getType()).thenReturn(Material.IRON_HELMET);
		when(chest.getType()).thenReturn(Material.IRON_CHESTPLATE);
		listener.onCrime(crime(false));
		WantedEndEvent end = mock(WantedEndEvent.class);
		when(end.getPlayer()).thenReturn(player);

		listener.onChaseEnd(end);

		assertNull(descriptions.latest(id));
		verify(tick).forget(id);
		assertTrue(outfit.hides(player, 1), "the next chase starts with an unblown outfit");
	}

	@Test
	@DisplayName("a player quitting while wanted drops the tick's squad at MONITOR")
	void quitForgetsTheSquad() throws Exception {
		java.lang.reflect.Method method = SuspectListener.class.getMethod("onQuit",
		                                                                  org.bukkit.event.player.PlayerQuitEvent.class);
		assertEquals(EventPriority.MONITOR, method.getAnnotation(EventHandler.class).priority());
		org.bukkit.event.player.PlayerQuitEvent quit = mock(org.bukkit.event.player.PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(player);

		method.invoke(listener, quit);

		verify(tick).forget(id);
	}
}
