package org.luckyraven.gangland.turf.listener;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TF-44 (claim half) and D8 = B: capturing a turf from a bountied gang moves the whole bounty into the capturing gang's
 * bank and resets the bounty. Money is moved, never minted. An unclaimed turf, an ally, the same gang or a gang with no
 * bounty moves nothing.
 */
@DisplayName("TurfGangBountyListener - taking a turf from a bountied gang collects the bounty")
class TurfGangBountyListenerTest {

	private static final BigDecimal BOUNTY = new BigDecimal("1500");

	private UserLookupContract users;
	private GangMessages       messages;
	private TurfGangBountyListener listener;
	private Turf                turf;

	@BeforeEach
	void setUp() {
		users    = mock(UserLookupContract.class);
		messages = mock(GangMessages.class);
		when(messages.text(anyString(), anyString(), any(Object[].class))).thenAnswer(invocation -> invocation.getArgument(0));
		listener = new TurfGangBountyListener(users, messages);

		turf = mock(Turf.class);
		when(turf.getDisplayName()).thenReturn("Docks");
	}

	@Test
	@DisplayName("the capturing gang banks the bounty, the bounty resets, and both gangs' online members are told")
	void capture_collectsBountyIntoCapturersBank() {
		Gang     vipers   = gang(7, "Vipers", bountyOf(BOUNTY));
		Player   loser    = player();
		Gang     rats     = gang(5, "Rats", new Bounty(BigDecimal.ZERO, 0));
		Player   winner   = player();
		User<Player> loserUser  = member(loser);
		User<Player> winnerUser = member(winner);
		when(vipers.getOnlineMembers(any())).thenReturn(List.of(loserUser));
		when(rats.getOnlineMembers(any())).thenReturn(List.of(winnerUser));

		try (MockedStatic<Settings> settings = settings()) {
			listener.onCaptured(new TurfCapturedEvent(turf, vipers, rats));
		}

		assertEquals(0, BOUNTY.compareTo(rats.getEconomy().getAmount()), "rats bank " + rats.getEconomy().getAmount());
		assertFalse(vipers.getBounty().hasBounty());
		verify(loser).sendMessage("Bounty.Lost");
		verify(winner).sendMessage("Bounty.Claimed");
	}

	@Test
	@DisplayName("an allied capturing gang collects nothing")
	void allied_nothingMoves() {
		Gang vipers = gang(7, "Vipers", bountyOf(BOUNTY));
		Gang rats   = gang(5, "Rats", new Bounty(BigDecimal.ZERO, 0));
		when(rats.isAlly(vipers)).thenReturn(true);

		try (MockedStatic<Settings> settings = settings()) {
			listener.onCaptured(new TurfCapturedEvent(turf, vipers, rats));
		}

		assertEquals(0, BigDecimal.ZERO.compareTo(rats.getEconomy().getAmount()));
		assertEquals(0, BOUNTY.compareTo(vipers.getBounty().getAmount()));
	}

	@Test
	@DisplayName("the same gang taking its own turf collects nothing")
	void sameGang_nothingMoves() {
		Gang vipers = gang(7, "Vipers", bountyOf(BOUNTY));

		try (MockedStatic<Settings> settings = settings()) {
			listener.onCaptured(new TurfCapturedEvent(turf, vipers, vipers));
		}

		assertEquals(0, BOUNTY.compareTo(vipers.getBounty().getAmount()));
		assertEquals(0, BigDecimal.ZERO.compareTo(vipers.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("an unclaimed turf (no old owner) collects nothing")
	void unclaimed_nothingMoves() {
		Gang rats = gang(5, "Rats", new Bounty(BigDecimal.ZERO, 0));

		try (MockedStatic<Settings> settings = settings()) {
			listener.onCaptured(new TurfCapturedEvent(turf, null, rats));
		}

		assertEquals(0, BigDecimal.ZERO.compareTo(rats.getEconomy().getAmount()));
	}

	@Test
	@DisplayName("a previous owner with no bounty pays nothing and nobody is messaged")
	void noBounty_nothingMoves() {
		Gang vipers = gang(7, "Vipers", new Bounty(BigDecimal.ZERO, 0));
		Gang rats   = gang(5, "Rats", new Bounty(BigDecimal.ZERO, 0));

		try (MockedStatic<Settings> settings = settings()) {
			listener.onCaptured(new TurfCapturedEvent(turf, vipers, rats));
		}

		assertEquals(0, BigDecimal.ZERO.compareTo(rats.getEconomy().getAmount()));
		verify(messages, never()).text(anyString(), anyString(), any(Object[].class));
	}

	/** A bounty booked the way the post command books it, so {@link Bounty#hasBounty()} is true. */
	private static Bounty bountyOf(BigDecimal amount) {
		Bounty bounty = new Bounty(BigDecimal.ZERO, 0);
		bounty.addBounty(mock(CommandSender.class), amount);
		return bounty;
	}

	private Gang gang(int id, String name, Bounty bounty) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayNameString()).thenReturn(name);
		when(gang.getBounty()).thenReturn(bounty);
		when(gang.getEconomy()).thenReturn(new EconomyHandler(null));
		when(gang.getOnlineMembers(any())).thenReturn(List.of());
		return gang;
	}

	private static Player player() {
		return mock(Player.class);
	}

	private User<Player> member(Player player) {
		User<Player> user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		return user;
	}

	/** Money formats and the currency symbol are stubbed; the listener only reads them for the message pairs. */
	private static MockedStatic<Settings> settings() {
		MockedStatic<Settings> settings = mockStatic(Settings.class);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(() -> Settings.formatAmount(any())).thenReturn("1500");
		return settings;
	}

}
