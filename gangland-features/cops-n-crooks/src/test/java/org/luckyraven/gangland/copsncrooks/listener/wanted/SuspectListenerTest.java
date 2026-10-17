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
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseTick;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.PoliceOutfit;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescription;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.SuspectDescriptions;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.WearableKeys;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
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
		listener     = new SuspectListener(descriptions, outfit, tick);
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

	@Test
	@DisplayName("onCrime runs at MONITOR and skips cancelled (witness-gated) crimes")
	void crimeHandlerIsMonitorIgnoreCancelled() throws NoSuchMethodException {
		EventHandler handler = SuspectListener.class.getMethod("onCrime", CrimeCommittedEvent.class)
		                                            .getAnnotation(EventHandler.class);

		assertNotNull(handler);
		assertEquals(EventPriority.MONITOR, handler.priority());
		assertTrue(handler.ignoreCancelled(), "a cancelled crime must not be snapshotted");
	}

	@Test
	@DisplayName("a crime stores the description of the player as they look right now")
	void crimeStoresDescription() {
		listener.onCrime(crime(false));

		SuspectDescription stored = descriptions.latest(id);
		assertNotNull(stored);
		assertTrue(stored.masked());
		assertEquals("carved pumpkin", stored.head());
		assertEquals("red jacket", stored.chest());
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
