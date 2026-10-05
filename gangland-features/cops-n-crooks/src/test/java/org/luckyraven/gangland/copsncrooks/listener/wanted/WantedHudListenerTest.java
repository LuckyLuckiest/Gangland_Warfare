package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.hud.HudFixtures;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WantedHudListener}: the title with the star card, the siren, the card in chat when the title is off, the
 * escape card of a getaway, and the star-chat suppression the constructor installs.
 */
@DisplayName("WantedHudListener")
class WantedHudListenerTest {

	@TempDir
	Path tempDir;

	private BukkitStatics bukkit;
	private JavaPlugin    plugin;
	private HeatLedger    ledger;
	private WantedStars   stars;
	private Wanted        wanted;
	private Player        player;
	private BossBar       bar;
	private WantedMessages messages;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	@BeforeEach
	void setUp() throws IOException {
		bukkit = BukkitStatics.install();
		bar    = mock(BossBar.class);
		bukkit.statics()
		      .when(() -> Bukkit.createBossBar(anyString(), any(BarColor.class), any(BarStyle.class)))
		      .thenReturn(bar);

		plugin   = mock(JavaPlugin.class);
		ledger   = mock(HeatLedger.class);
		stars    = mock(WantedStars.class);
		messages = HudFixtures.messages(tempDir);

		wanted = mock(Wanted.class);
		when(wanted.getMaxLevel()).thenReturn(5);

		player = mock(Player.class);
		UUID id = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		// XSound addresses its listener by uuid, through Bukkit.getPlayer
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
		Location here = new Location(mock(World.class), 0, 64, 0);
		when(player.getLocation()).thenReturn(here);
		when(player.getEyeLocation()).thenReturn(here);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private WantedHudListener listener(HudSettings hud) {
		CopTierConfig tier = mock(CopTierConfig.class);
		when(tier.displayName()).thenReturn("&9Sergeant");
		when(tier.skipCuffing()).thenReturn(false);

		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getTierConfig(anyInt())).thenReturn(tier);

		CopLoader copLoader = mock(CopLoader.class);
		when(copLoader.getLoadedProvider()).thenReturn(provider);

		CopSpawnManager spawns = mock(CopSpawnManager.class);
		when(spawns.getTierForWantedLevel(anyInt())).thenAnswer(call -> call.getArgument(0));

		when(ledger.lastCrime(player.getUniqueId())).thenReturn(
				new CrimeRecord("Assault_Cop", 100, 0L, new Location(mock(World.class), 0, 64, 0)));

		return new WantedHudListener(plugin, HudFixtures.loader(hud), messages, ledger, spawns, copLoader, stars);
	}

	private WantedLevelChangeEvent change(int from, int to, WantedCause cause) {
		return new WantedLevelChangeEvent(player, wanted, from, to, cause);
	}

	private long playSoundCalls() {
		return Mockito.mockingDetails(player)
		              .getInvocations()
		              .stream()
		              .filter(call -> call.getMethod().getName().equals("playSound"))
		              .count();
	}

	@Test
	@DisplayName("the constructor makes the card replace the star chat line, and starts the half-second beat")
	void listenerConstructor_suppressesTheStarChatLine() {
		listener(HudSettings.DEFAULT);

		ArgumentCaptor<BooleanSupplier> suppressor = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(stars).suppressStarChat(suppressor.capture());
		assertTrue(suppressor.getValue().getAsBoolean());
		verify(bukkit.scheduler()).runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L));
	}

	@Test
	@DisplayName("a raise shows the stars as the title, the card as its subtitle, and plays the siren once")
	void raise_sendsTitleWithTheCardAsSubtitle_andPlaysTheSiren() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		ArgumentCaptor<String> title    = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(title.capture(), subtitle.capture(), eq(5), eq(40), eq(10));
		assertEquals("★★☆☆☆", ChatColor.stripColor(title.getValue()));
		assertEquals("Assault on an officer: Sergeant inbound, they still want you in cuffs",
		             ChatColor.stripColor(subtitle.getValue()));
		assertEquals(1, playSoundCalls());
	}

	@Test
	@DisplayName("with Title off and Star_Card on the card goes to chat")
	void titleOff_starCardOn_sendsTheCardToChat() {
		WantedHudListener listener = listener(HudFixtures.hud(true, true, false, true, true, true));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		ArgumentCaptor<String> chat = ArgumentCaptor.forClass(String.class);
		verify(player).sendMessage(chat.capture());
		assertEquals("Assault on an officer: Sergeant inbound, they still want you in cuffs",
		             ChatColor.stripColor(chat.getValue()));
		verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("with Title on and Star_Card off the subtitle is empty")
	void titleOn_starCardOff_sendsAnEmptySubtitle() {
		WantedHudListener listener = listener(HudFixtures.hud(true, false, true, true, true, true));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		verify(player).sendTitle(anyString(), eq(""), eq(5), eq(40), eq(10));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("with Siren off a raise plays no sound")
	void sirenOff_playsNoSound() {
		WantedHudListener listener = listener(HudFixtures.hud(true, true, true, false, true, true));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		assertEquals(0, playSoundCalls());
		verify(player).sendTitle(anyString(), anyString(), eq(5), eq(40), eq(10));
	}

	@Test
	@DisplayName("a drop to one star by evasion sends the evasion card and no siren")
	void dropToOneStar_sendsTheEvasionDropCard() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);

		listener.onLevelChange(change(2, 1, WantedCause.EVASION));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), eq(5), eq(40), eq(10));
		assertEquals("You stayed out of sight", ChatColor.stripColor(subtitle.getValue()));
		assertEquals(0, playSoundCalls());
	}

	@Test
	@DisplayName("losing the last star by evasion sends the escape card, then the bar goes with the chase")
	void lastStarDropByEvasion_sendsTheEscapeCard_thenHides() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));

		listener.onLevelChange(change(1, 0, WantedCause.EVASION));
		verify(bar, never()).removeAll();

		listener.onWantedEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), eq(5), eq(40), eq(10));
		assertEquals("You stayed out of sight", ChatColor.stripColor(subtitle.getValue()));
		verify(bar).removeAll();
	}
}
