package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
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
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.hud.HudFixtures;
import org.luckyraven.gangland.copsncrooks.wanted.hud.TitleCue;
import org.luckyraven.gangland.copsncrooks.events.police.ArrestedEvent;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
	private ChaseArcs      arcs;

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
		arcs     = new ChaseArcs(() -> 0L);
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

	private PostEscapeSearch search;

	/** The search as production builds it: a user behind it (zero bounty), so {@code begin} marks the player. */
	private PostEscapeSearch searchWithUser() {
		Bounty bounty = mock(Bounty.class);
		when(bounty.getPostedAmount()).thenReturn(BigDecimal.ZERO);
		when(bounty.getNotoriety()).thenReturn(BigDecimal.ZERO);
		User<Player> user = mock(User.class);
		when(user.getBounty()).thenReturn(bounty);
		when(user.getLevel()).thenReturn(mock(Level.class));
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);
		return new PostEscapeSearch(users);
	}

	/** The escape's search start, as {@link PostEscapeListener} runs it on the last star's level change. */
	private void beginSearch() {
		new PostEscapeListener(search).onLevelChange(change(1, 0, WantedCause.EVASION));
	}

	private WantedHudListener listener(HudSettings hud) {
		return listener(HudFixtures.loader(hud));
	}

	private WantedHudListener listener(ChaseConfigLoader chase) {
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

		search = searchWithUser();
		return new WantedHudListener(plugin, chase, messages, ledger, spawns, copLoader, stars, arcs, search);
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
	@DisplayName("with Star_Card off the suppressor answers false, so the core's star chat line comes back")
	void starCardOff_suppressorAnswersFalse_soTheChatLineReturns() {
		listener(HudFixtures.hud(true, false, true, true, true, true));

		ArgumentCaptor<BooleanSupplier> suppressor = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(stars).suppressStarChat(suppressor.capture());
		assertFalse(suppressor.getValue().getAsBoolean());
	}

	@Test
	@DisplayName("the suppressor reads Star_Card per call, so a reload that turns the card off brings the chat line back")
	void starCardSwitch_isReadPerCall() {
		ChaseConfigLoader chase = HudFixtures.loader(HudSettings.DEFAULT);
		listener(chase);
		ArgumentCaptor<BooleanSupplier> suppressor = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(stars).suppressStarChat(suppressor.capture());
		assertTrue(suppressor.getValue().getAsBoolean());

		ChaseConfig d = ChaseConfig.DEFAULT;
		when(chase.get()).thenReturn(new ChaseConfig(d.heat(), d.evasion(),
		                                             HudFixtures.hud(true, false, true, true, true, true),
		                                             d.chargeSheet()));

		assertFalse(suppressor.getValue().getAsBoolean());
	}

	@Test
	@DisplayName("a raise shows the stars as the title, the card as its subtitle, and plays the siren once")
	void raise_sendsTitleWithTheCardAsSubtitle_andPlaysTheSiren() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		ArgumentCaptor<String> title    = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(title.capture(), subtitle.capture(), eq(5), eq(20), eq(5));
		assertEquals("", ChatColor.stripColor(title.getValue()));
		assertEquals("★★☆☆☆ Assault on an officer: Sergeant inbound, they still want you in cuffs",
		             ChatColor.stripColor(subtitle.getValue()));
		assertEquals(1, playSoundCalls());
	}

	@Test
	@DisplayName("a raise that is not a crime ignores the ledger and names the reported crime")
	void raise_nonCrimeCause_usesReportedCrime() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);

		listener.onLevelChange(change(1, 2, WantedCause.ADMIN));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), eq(5), eq(20), eq(5));
		assertEquals("★★☆☆☆ Reported crime: Sergeant inbound, they still want you in cuffs",
		             ChatColor.stripColor(subtitle.getValue()));
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
	@DisplayName("with Title on and Star_Card off the subtitle is the star row alone, and nothing goes to chat")
	void titleOn_starCardOff_sendsTheStarRowOnly() {
		WantedHudListener listener = listener(HudFixtures.hud(true, false, true, true, true, true));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		verify(player).sendTitle(ChatColor.RESET.toString(), "★★☆☆☆", 5, 20, 5);
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a star gained with both Boss_Bar and Star_Card off still names the count in the subtitle")
	void gain_bossBarOff_starCardOff_namesTheCount() {
		HudSettings hud = new HudSettings(false, false, TitleCue.DEFAULT, TitleCue.DEFAULT, TitleCue.ESCAPED, true,
		                                  HudSettings.DEFAULT.sirenSound(), HudSettings.DEFAULT.sirenVolume(),
		                                  HudSettings.DEFAULT.sirenPitch(), true, HudSettings.DEFAULT.zoneParticle(),
		                                  HudSettings.DEFAULT.zonePoints(), true);
		WantedHudListener listener = listener(hud);

		listener.onLevelChange(change(2, 3, WantedCause.CRIME));

		verify(player).sendTitle(ChatColor.RESET.toString(), "★★★☆☆", 5, 20, 5);
	}

	@Test
	@DisplayName("with Siren off a raise plays no sound")
	void sirenOff_playsNoSound() {
		WantedHudListener listener = listener(HudFixtures.hud(true, true, true, false, true, true));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		assertEquals(0, playSoundCalls());
		verify(player).sendTitle(anyString(), anyString(), eq(5), eq(20), eq(5));
	}

	@Test
	@DisplayName("a drop to one star by evasion sends the evasion card and no siren")
	void dropToOneStar_sendsTheEvasionDropCard() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);

		listener.onLevelChange(change(2, 1, WantedCause.EVASION));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), eq(5), eq(20), eq(5));
		assertEquals("★☆☆☆☆ You stayed out of sight", ChatColor.stripColor(subtitle.getValue()));
		assertEquals(0, playSoundCalls());
	}

	@Test
	@DisplayName("losing the last star by evasion sends the escape card, and the bar stays up for the bounty")
	void lastStarDropByEvasion_sendsTheEscapeCard_thenHides() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));

		listener.onLevelChange(change(1, 0, WantedCause.EVASION));
		verify(bar, never()).removeAll();

		beginSearch();
		listener.onWantedEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), eq(5), eq(20), eq(5));
		assertEquals("You stayed out of sight", ChatColor.stripColor(subtitle.getValue()));
		verify(bar, never()).removeAll();
	}

	@Test
	@DisplayName("a post-escape bounty keeps the bar up; the expiry removes it and says the cops gave up the search")
	void bountyBar_expiry_removesBar_andSaysGaveUp() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		ArgumentCaptor<Runnable> beat = ArgumentCaptor.forClass(Runnable.class);
		verify(bukkit.scheduler()).runTaskTimer(any(JavaPlugin.class), beat.capture(), eq(10L), eq(10L));
		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));

		beginSearch();
		listener.onWantedEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));
		verify(bar, never()).removeAll();

		// 300 beats of 10 ticks = 150 s, past the 120 s Search_Seconds proposed in the spec
		for (int i = 0; i < 300; i++) beat.getValue().run();

		verify(bar).removeAll();
		ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
		verify(player, atLeastOnce()).sendMessage(said.capture());
		assertTrue(said.getAllValues()
		               .stream()
		               .map(ChatColor::stripColor)
		               .anyMatch(line -> line.contains("cops gave up the search")));
	}

	@Test
	@DisplayName("a new wanted start takes the bar back from the bounty: the wanted title, never the bounty line")
	void reWanted_takesBarBackFromBounty() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));
		beginSearch();
		listener.onWantedEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));
		verify(bar, never()).removeAll();

		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));

		ArgumentCaptor<String> titles = ArgumentCaptor.forClass(String.class);
		verify(bar, atLeastOnce()).setTitle(titles.capture());
		assertFalse(ChatColor.stripColor(titles.getValue()).contains("Cops still looking"));
	}

	@Test
	@DisplayName("an evasion drop with a pending AUTO plan shows its ending card with the count, and consumes the plan")
	void evasionDrop_withPendingPlan_showsTheEndingCard_andConsumesIt() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		arcs.start(player.getUniqueId(), WantedCause.CRIME, 3);
		arcs.stashPending(player.getUniqueId(), new DropPlan(3, Ending.PETTY, "petty"));

		listener.onLevelChange(change(3, 0, WantedCause.EVASION));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), eq(5), eq(20), eq(5));
		assertEquals("Small fry, they dropped the case (-3)", ChatColor.stripColor(subtitle.getValue()));
		assertEquals(null, arcs.takePending(player.getUniqueId()));
	}

	@Test
	@DisplayName("an evasion drop with no pending plan keeps today's card")
	void evasionDrop_withoutPendingPlan_keepsTodaysCard() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		arcs.start(player.getUniqueId(), WantedCause.CRIME, 3);

		listener.onLevelChange(change(3, 2, WantedCause.EVASION));

		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(anyString(), subtitle.capture(), eq(5), eq(20), eq(5));
		assertEquals("★★☆☆☆ You stayed out of sight", ChatColor.stripColor(subtitle.getValue()));
	}

	@Test
	@DisplayName("a DECAY or ARREST drop never reads or consumes the pending plan")
	void decayAndArrestDrops_leaveThePendingPlanAlone() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		arcs.start(player.getUniqueId(), WantedCause.CRIME, 3);
		DropPlan plan = new DropPlan(3, Ending.PETTY, "petty");
		arcs.stashPending(player.getUniqueId(), plan);

		listener.onLevelChange(change(3, 2, WantedCause.DECAY));
		listener.onLevelChange(change(2, 1, WantedCause.ARREST));

		assertEquals(plan, arcs.takePending(player.getUniqueId()));
	}

	@Test
	@DisplayName("a drop of several stars puts -N STARS on the green bar")
	void multiStarDrop_tellsTheBar() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		listener.onStart(new WantedStartEvent(player, wanted, 4, WantedCause.CRIME));

		listener.onLevelChange(change(4, 1, WantedCause.EVASION));
		listener.onEvasionState(new org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent(
				player, org.luckyraven.gangland.events.wanted.EvasionState.EVADED, 1, 0, null, 0));

		verify(bar).setTitle(org.mockito.ArgumentMatchers.contains("-3 STARS"));
	}

	private static HudSettings cuesWith(boolean starCard, TitleCue gain, TitleCue lost, TitleCue escaped) {
		return HudFixtures.cues(starCard, gain, lost, escaped);
	}

	@Test
	@DisplayName("a gained star sends the configured title and subtitle with the placeholders filled and the fades passed")
	void gainCue_customTitleAndSubtitle_substitutesStarsLevelCard() {
		TitleCue gain = new TitleCue(true, "&c%stars% L%level%", "%card%", 2, 30, 3);
		WantedHudListener listener = listener(cuesWith(true, gain, TitleCue.DEFAULT, TitleCue.DEFAULT));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		ArgumentCaptor<String> title    = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> subtitle = ArgumentCaptor.forClass(String.class);
		verify(player).sendTitle(title.capture(), subtitle.capture(), eq(2), eq(30), eq(3));
		assertEquals("★★☆☆☆ L2", ChatColor.stripColor(title.getValue()));
		assertEquals("Assault on an officer: Sergeant inbound, they still want you in cuffs",
		             ChatColor.stripColor(subtitle.getValue()));
	}

	@Test
	@DisplayName("a disabled gain cue with Star_Card on sends the card to chat and no title")
	void gainCue_disabled_starCardOn_sendsCardToChat() {
		TitleCue off = new TitleCue(false, "", "%card%", 5, 20, 5);
		WantedHudListener listener = listener(cuesWith(true, off, TitleCue.DEFAULT, TitleCue.DEFAULT));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		ArgumentCaptor<String> chat = ArgumentCaptor.forClass(String.class);
		verify(player).sendMessage(chat.capture());
		assertEquals("Assault on an officer: Sergeant inbound, they still want you in cuffs",
		             ChatColor.stripColor(chat.getValue()));
		verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("a disabled gain cue with Star_Card off sends nothing at all")
	void gainCue_disabled_starCardOff_sendsNothing() {
		TitleCue off = new TitleCue(false, "", "%card%", 5, 20, 5);
		WantedHudListener listener = listener(cuesWith(false, off, TitleCue.DEFAULT, TitleCue.DEFAULT));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a blank title and subtitle after placeholders send nothing, and no chat fallback")
	void gainCue_blankTitleAndSubtitle_sendsNothing() {
		TitleCue blank = new TitleCue(true, "", "%card%", 5, 20, 5);
		WantedHudListener listener = listener(cuesWith(false, blank, TitleCue.DEFAULT, TitleCue.DEFAULT));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		verify(player, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a 2-to-1 evasion drop uses the Lost cue and a 1-to-0 evasion uses the Escaped cue")
	void lostAndEscapedUseTheirOwnCue() {
		TitleCue lost    = new TitleCue(true, "Lost %level%", "lost-text", 1, 2, 3);
		TitleCue escaped = new TitleCue(true, "Escaped", "escaped-text", 4, 5, 6);
		WantedHudListener listener = listener(cuesWith(true, TitleCue.DEFAULT, lost, escaped));

		listener.onLevelChange(change(2, 1, WantedCause.EVASION));
		verify(player).sendTitle("Lost 1", "lost-text", 1, 2, 3);

		listener.onLevelChange(change(1, 0, WantedCause.EVASION));
		verify(player).sendTitle("Escaped", "escaped-text", 4, 5, 6);
	}

	@Test
	@DisplayName("a disabled Escaped cue still consumes the pending AUTO plan")
	void disabledCue_stillConsumesPendingPlan() {
		TitleCue off = new TitleCue(false, "", "%card%", 5, 20, 5);
		WantedHudListener listener = listener(cuesWith(true, TitleCue.DEFAULT, TitleCue.DEFAULT, off));
		arcs.start(player.getUniqueId(), WantedCause.CRIME, 3);
		arcs.stashPending(player.getUniqueId(), new DropPlan(3, Ending.PETTY, "petty"));

		listener.onLevelChange(change(3, 0, WantedCause.EVASION));

		assertEquals(null, arcs.takePending(player.getUniqueId()));
	}

	@Test
	@DisplayName("a disabled Gain cue still plays the siren")
	void disabledCue_siren_stillPlays() {
		TitleCue off = new TitleCue(false, "", "%card%", 5, 20, 5);
		WantedHudListener listener = listener(cuesWith(true, off, TitleCue.DEFAULT, TitleCue.DEFAULT));

		listener.onLevelChange(change(1, 2, WantedCause.CRIME));

		assertEquals(1, playSoundCalls());
	}

	@Test
	@DisplayName("a death or a downing hides the bounty bar: the searched player is at zero stars, so no wanted end fires")
	void deathOrDowned_hidesTheBountyBar() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));
		beginSearch();
		listener.onWantedEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));
		verify(bar, never()).removeAll();

		PlayerDeathEvent death = mock(PlayerDeathEvent.class);
		when(death.getEntity()).thenReturn(player);
		listener.onDeath(death);

		verify(bar).removeAll();
	}

	@Test
	@DisplayName("an arrest of a searched player (zero stars, no wanted end) hides the bounty bar and ends the search")
	void arrestedWhileSearched_hidesTheBountyBar_andEndsTheSearch() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));
		beginSearch();
		listener.onWantedEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));
		UUID id = player.getUniqueId();
		assertTrue(search.isSearching(id));

		listener.onArrested(new ArrestedEvent(player));

		verify(bar).removeAll();
		assertFalse(search.isSearching(id));
	}

	@Test
	@DisplayName("an escape that started no search shows no bounty bar and no give-up line")
	void escapeWithNoSearch_showsNoBountyBar() {
		WantedHudListener listener = listener(HudSettings.DEFAULT);
		ArgumentCaptor<Runnable> beat = ArgumentCaptor.forClass(Runnable.class);
		verify(bukkit.scheduler()).runTaskTimer(any(JavaPlugin.class), beat.capture(), eq(10L), eq(10L));
		listener.onStart(new WantedStartEvent(player, wanted, 1, WantedCause.CRIME));

		listener.onWantedEnd(new WantedEndEvent(player, wanted, WantedCause.EVASION));

		// the chase bar goes with no bounty on it; nothing keeps drawing it (0.16.1 wanted-2)
		verify(bar).removeAll();
		ArgumentCaptor<String> titles = ArgumentCaptor.forClass(String.class);
		verify(bar, atLeastOnce()).setTitle(titles.capture());
		assertTrue(titles.getAllValues()
		                 .stream()
		                 .map(ChatColor::stripColor)
		                 .noneMatch(line -> line.contains("Cops still looking")));
		for (int i = 0; i < 300; i++) beat.getValue().run();
		verify(player, never()).sendMessage(argThat((String line) -> line.contains("cops gave up the search")));
	}
}
