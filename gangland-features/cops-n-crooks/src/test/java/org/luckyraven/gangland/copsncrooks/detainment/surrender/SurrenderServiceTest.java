package org.luckyraven.gangland.copsncrooks.detainment.surrender;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.settings.SurrenderSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 Surrender (P-SURR-1..7): crouch with empty hands while the squad has eyes on you at 1 to 3 stars and the group
 * holds fire; standing up ends it quietly; a hit inside the resist window is Resisting_Arrest.
 */
@DisplayName("SurrenderService")
class SurrenderServiceTest {

	private static final long T0 = 1_000_000L;

	private final UUID playerId = UUID.randomUUID();

	private Player            player;
	private PlayerInventory   inventory;
	private CopGroup          group;
	private CopRadio          radio;
	private WantedMessages    messages;
	private CrimeService      crimes;
	private Location          location;
	private AtomicLong        clock;
	private AtomicBoolean     restrained;
	private SurrenderSettings settings;
	private SurrenderService  service;

	@BeforeEach
	void setUp() {
		player    = mock(Player.class);
		inventory = mock(PlayerInventory.class);
		location  = mock(Location.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getLocation()).thenReturn(location);
		when(location.clone()).thenReturn(location);
		mainHand(Material.AIR);
		offHand(Material.AIR);
		when(player.isSneaking()).thenReturn(true);

		group = new CopGroup(playerId);
		group.setLevel(2);
		group.getSquad().reportSighting(location);

		radio    = mock(CopRadio.class);
		messages = mock(WantedMessages.class);
		when(messages.format(any(), anyMap())).thenReturn("line");
		crimes     = mock(CrimeService.class);
		clock      = new AtomicLong(T0);
		restrained = new AtomicBoolean();
		settings   = new SurrenderSettings(true, 3, 0.5, 0.5, 0.75, 10);

		service = new SurrenderService(() -> settings, radio, messages, crimes, clock::get, p -> restrained.get());
	}

	private static ItemStack stack(Material type) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(type);
		return stack;
	}

	private void mainHand(Material type) {
		ItemStack stack = stack(type);
		when(inventory.getItemInMainHand()).thenReturn(stack);
	}

	private void offHand(Material type) {
		ItemStack stack = stack(type);
		when(inventory.getItemInOffHand()).thenReturn(stack);
	}

	private void sneak(boolean sneaking) {
		when(player.isSneaking()).thenReturn(sneaking);
	}

	private void surrender() {
		service.tick(player, group);
		assertTrue(group.isCeaseFire(), "precondition: surrendered");
	}

	private void hit() {
		service.onCopAttacked(mock(CopNpc.class), player);
	}

	@Test
	@DisplayName("crouch with empty hands, seen at 2 stars: cease-fire on, radio and chat once across ticks")
	void crouchEmptyHandsSeen_setsCeaseFireAndRadiosOnce() {
		service.tick(player, group);
		service.tick(player, group);

		assertTrue(group.isCeaseFire());
		verify(radio, times(1)).sayFromLeader(group, "Surrender_Accepted");
		verify(messages, times(1)).format(eq(WantedMessages.Key.SURRENDER_ACCEPTED), anyMap());
		verify(player, times(1)).sendMessage("line");
	}

	@Test
	@DisplayName("a white banner in the main hand with the off hand empty counts as empty hands")
	void whiteBannerCountsAsEmptyHands() {
		mainHand(Material.WHITE_BANNER);

		service.tick(player, group);

		assertTrue(group.isCeaseFire());
	}

	@Test
	@DisplayName("a banner in the main hand with an item in the off hand is no surrender")
	void bannerWithOffHandItem_noSurrender() {
		mainHand(Material.WHITE_BANNER);
		offHand(Material.TORCH);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("a null hand slot counts as empty")
	void nullHands_countAsEmpty() {
		when(inventory.getItemInMainHand()).thenReturn(null);
		when(inventory.getItemInOffHand()).thenReturn(null);

		service.tick(player, group);

		assertTrue(group.isCeaseFire());
	}

	@Test
	@DisplayName("a sword in the main hand: no surrender")
	void itemInHand_noSurrender() {
		mainHand(Material.IRON_SWORD);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		verifyNoInteractions(radio);
	}

	@Test
	@DisplayName("a torch in the off hand: no surrender")
	void offHandItem_noSurrender() {
		offHand(Material.TORCH);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("standing with empty hands: no surrender")
	void notSneaking_noSurrender() {
		sneak(false);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("no fresh sighting: the cops cannot see him, no surrender")
	void notSeen_noSurrender() {
		CopGroup unseen = new CopGroup(playerId);
		unseen.setLevel(2);

		service.tick(player, unseen);

		assertFalse(unseen.isCeaseFire());
		verifyNoInteractions(radio);
	}

	@Test
	@DisplayName("level 3 at Max_Stars 3 is accepted (the bound is inclusive)")
	void atMaxStars_accepted() {
		group.setLevel(3);

		service.tick(player, group);

		assertTrue(group.isCeaseFire());
	}

	@Test
	@DisplayName("level 4 over Max_Stars 3: no flag, the too-hot line once across ticks")
	void aboveMaxStars_tooHotOnce() {
		group.setLevel(4);

		service.tick(player, group);
		service.tick(player, group);
		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		verify(messages, times(1)).format(eq(WantedMessages.Key.SURRENDER_TOO_HOT), anyMap());
		verify(player, times(1)).sendMessage("line");
		verifyNoInteractions(radio);
	}

	@Test
	@DisplayName("the too-hot line is said again in the next chase (group gone in between)")
	void tooHot_saidAgainNextChase() {
		group.setLevel(4);
		service.tick(player, group);
		service.tick(player, null);

		service.tick(player, group);

		verify(messages, times(2)).format(eq(WantedMessages.Key.SURRENDER_TOO_HOT), anyMap());
	}

	@Test
	@DisplayName("zero stars: nothing happens, not even the too-hot line")
	void zeroStars_noSurrender() {
		group.setLevel(0);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		verifyNoInteractions(radio);
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a raised Max_Stars is read live: level 4 at Max_Stars 5 surrenders")
	void maxStarsIsReadLive() {
		settings = new SurrenderSettings(true, 5, 0.5, 0.5, 0.75, 10);
		group.setLevel(4);

		service.tick(player, group);

		assertTrue(group.isCeaseFire());
	}

	@Test
	@DisplayName("standing up after surrendering: flag cleared, no crime, no escalation")
	void standUp_clearsFlag() {
		surrender();
		sneak(false);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		assertFalse(group.isCombatAlert());
		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("dropping the pose by pulling out an item also ends it")
	void pullingOutAnItem_endsSurrender() {
		surrender();
		mainHand(Material.IRON_SWORD);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("standing up, then crouching again surrenders again with a fresh radio call")
	void standUpThenCrouch_surrendersAgain() {
		surrender();
		sneak(false);
		service.tick(player, group);
		sneak(true);

		service.tick(player, group);

		assertTrue(group.isCeaseFire());
		verify(radio, times(2)).sayFromLeader(group, "Surrender_Accepted");
	}

	@Test
	@DisplayName("a hit 9 s after surrendering commits Resisting_Arrest once and prints the resisted line")
	void hitInsideWindow_resistingArrest() {
		surrender();
		clock.set(T0 + 9_000L);

		hit();

		verify(crimes, times(1)).commit(player, Crimes.RESISTING_ARREST, location);
		verify(messages).format(eq(WantedMessages.Key.SURRENDER_RESISTED), anyMap());
	}

	@Test
	@DisplayName("a hit exactly at the window edge (10 s) still counts")
	void hitAtWindowEdge_stillResisting() {
		surrender();
		clock.set(T0 + 10_000L);

		hit();

		verify(crimes).commit(player, Crimes.RESISTING_ARREST, location);
	}

	@Test
	@DisplayName("a hit 11 s after surrendering is an ordinary assault: no Resisting_Arrest, no resisted line")
	void hitAfterWindow_noResistingCrime() {
		surrender();
		clock.set(T0 + 11_000L);

		hit();

		verifyNoInteractions(crimes);
		verify(messages, never()).format(eq(WantedMessages.Key.SURRENDER_RESISTED), anyMap());
	}

	@Test
	@DisplayName("a second hit commits nothing more: the surrender is over after the first")
	void secondHit_commitsNothingMore() {
		surrender();
		clock.set(T0 + 2_000L);
		hit();

		hit();

		verify(crimes, times(1)).commit(any(), eq(Crimes.RESISTING_ARREST), any());
	}

	@Test
	@DisplayName("a hit by a player who never surrendered commits nothing")
	void hitWithoutSurrender_commitsNothing() {
		hit();

		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("after a resist the flag is not re-armed while still crouching; stand up then crouch arms it again")
	void afterResist_noRearmUntilPoseRetaken() {
		surrender();
		clock.set(T0 + 1_000L);
		hit();
		group.escalate(playerId); // what CopManager does right after the hook

		service.tick(player, group);
		assertFalse(group.isCeaseFire(), "still crouching after the resist: not re-armed");

		sneak(false);
		service.tick(player, group);
		sneak(true);
		service.tick(player, group);
		assertTrue(group.isCeaseFire(), "pose retaken: armed again");
	}

	@Test
	@DisplayName("disabled: nothing happens")
	void disabled_noop() {
		settings = new SurrenderSettings(false, 3, 0.5, 0.5, 0.75, 10);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		verifyNoInteractions(radio);
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("switched off while surrendered (reload): the flag is cleared")
	void disabledMidSurrender_clearsFlag() {
		surrender();
		settings = new SurrenderSettings(false, 3, 0.5, 0.5, 0.75, 10);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("cuffed (restrained) while surrendered: forgotten, the flag is cleared, a later hit commits nothing")
	void restrained_forgetsAndClears() {
		surrender();
		restrained.set(true);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		clock.set(T0 + 1_000L);
		hit();
		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("a restrained player never starts a surrender")
	void restrained_neverStarts() {
		restrained.set(true);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		verifyNoInteractions(radio);
	}

	@Test
	@DisplayName("the group vanishing (wanted ended, quit) forgets him: a later hit commits nothing")
	void groupGone_forgets() {
		surrender();

		service.tick(player, null);

		clock.set(T0 + 1_000L);
		hit();
		verifyNoInteractions(crimes);
	}

	@Test
	@DisplayName("no group at all: tick is a quiet no-op")
	void nullGroup_noop() {
		service.tick(player, null);

		verifyNoInteractions(radio, crimes);
	}

	@Test
	@DisplayName("a second tick while still posed does not end it and does not repeat the radio call")
	void secondTickDoesNotRepeat() {
		surrender();

		service.tick(player, group);

		assertTrue(group.isCeaseFire());
		verify(radio, times(1)).sayFromLeader(group, "Surrender_Accepted");
	}

	@Test
	@DisplayName("P-SURR-6: the too-hot line is said once per chase even when the player toggles crouch")
	void tooHot_onceAcrossCrouchToggles() {
		group.setLevel(4);

		service.tick(player, group);
		sneak(false);
		service.tick(player, group);
		sneak(true);
		service.tick(player, group);

		verify(messages, times(1)).format(eq(WantedMessages.Key.SURRENDER_TOO_HOT), anyMap());
		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("P-SURR-6: stars rising above Max_Stars while surrendered end it, with the too-hot line once")
	void starsRiseAboveMax_whileSurrendered_endsIt() {
		group.setLevel(2);
		surrender();
		group.setLevel(4);

		service.tick(player, group);

		assertFalse(group.isCeaseFire());
		verify(messages, times(1)).format(eq(WantedMessages.Key.SURRENDER_TOO_HOT), anyMap());
		clock.set(T0 + 1_000L);
		hit();
		verifyNoInteractions(crimes);
	}
}
