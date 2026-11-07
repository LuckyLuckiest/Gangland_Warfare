package org.luckyraven.gangland.copsncrooks.detainment.intake;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventoryService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterisation of which jail {@code JailIntakeService#admit} picks: the first jail with room is the seed; after
 * that a jail in the player's world beats one that is not, and among same-world jails the strictly nearest wins (a tie
 * keeps the earlier one); a jail in another world, or with no world, never wins over a same-world one and is never
 * measured (measuring across worlds throws).
 */
@DisplayName("JailIntakeService - which jail a new inmate is booked into")
class JailIntakePickJailTest {

	private final UUID          id         = UUID.randomUUID();
	private final World         home       = mock(World.class);
	private final World         elsewhere  = mock(World.class);
	private final JailRegistry  jails      = new JailRegistry();
	private final JailService   jailService = mock(JailService.class);
	private final DetainmentService detainment = mock(DetainmentService.class);
	private final Player        player     = mock(Player.class);
	private final BukkitStatics bukkit     = BukkitStatics.install(); // admit fires ArrestedEvent through Bukkit

	private JailIntakeService intake;

	@BeforeEach
	void setUp() {
		when(player.isOnline()).thenReturn(true);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
		when(player.getWorld()).thenReturn(home);
		when(player.getLocation()).thenReturn(new Location(home, 0, 64, 0));
		intake = new JailIntakeService(detainment, new DetainmentRegistry(mock(IRepository.class), jails), jailService,
		                               jails, mock(SeizedInventoryService.class), mock(WantedClearContract.class),
		                               mock(PaperworkItemFactory.class), mock(DetainmentCostsContract.class),
		                               mock(DetainmentSoundContract.class), mock(DetainmentEconomyContract.class),
		                               mock(ChaseConfigLoader.class), mock(HeatLedger.class), mock(WantedMessages.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private void jail(int jailId, World world, double x) {
		jails.setJailLocation(jailId, new Location(world, x, 64, 0), 5);
	}

	private void assertBookedInto(int jailId) {
		assertTrue(intake.admit(player, true));
		verify(jailService).detainPlayer(eq(jailId), eq(id));
		verify(jailService, never()).detainPlayer(eq(jailId == 1 ? 2 : 1), any());
	}

	@Test
	@DisplayName("among same-world jails the nearest wins, wherever it sits in the list")
	void sameWorld_nearestWins() {
		jail(1, home, 50);
		jail(2, home, 5);
		jail(3, home, 20);

		assertBookedInto(2);
	}

	@Test
	@DisplayName("a tie in distance keeps the earlier jail")
	void tie_keepsTheEarlier() {
		jail(1, home, 10);
		jail(2, home, -10);

		assertBookedInto(1);
	}

	@Test
	@DisplayName("a same-world jail beats an other-world seed, even a far one, and is not measured against it")
	void sameWorldBeatsAnOtherWorldSeed() {
		jail(1, elsewhere, 1);
		jail(2, home, 500);

		assertBookedInto(2);
	}

	@Test
	@DisplayName("an other-world jail after a same-world one is skipped without being measured")
	void otherWorldAfterSameWorld_isSkipped() {
		jail(1, home, 500);
		jail(2, elsewhere, 1);

		assertBookedInto(1);
	}

	@Test
	@DisplayName("only other-world jails: the first with room is kept")
	void onlyOtherWorlds_firstKept() {
		jail(1, elsewhere, 1);
		jail(2, elsewhere, 0);

		assertBookedInto(1);
	}

	@Test
	@DisplayName("an other-world seed, then a far same-world jail, then a nearer one: the nearer one")
	void otherWorldSeed_thenFarThenNear() {
		jail(1, elsewhere, 1);
		jail(2, home, 50);
		jail(3, home, 5);

		assertTrue(intake.admit(player, true));
		verify(jailService).detainPlayer(eq(3), eq(id));
		verify(jailService, never()).detainPlayer(eq(1), any());
		verify(jailService, never()).detainPlayer(eq(2), any());
	}

	@Test
	@DisplayName("a jail whose world is not loaded counts as not in the player's world")
	void noWorld_countsAsElsewhere() {
		jail(1, null, 1);
		jail(2, home, 500);

		assertBookedInto(2);
	}

	@Test
	@DisplayName("a full jail is skipped; the next one with room is the seed")
	void fullJail_isSkipped() {
		jails.setJailLocation(1, new Location(home, 1, 64, 0), 0);
		jail(2, elsewhere, 1);

		assertBookedInto(2);
	}

	@Test
	@DisplayName("with no jail that has room, nobody is booked and the player is released")
	void noRoom_releases() {
		jails.setJailLocation(1, new Location(home, 1, 64, 0), 0);

		assertFalse(intake.admit(player, true));

		verify(detainment).release(player);
		verify(jailService, never()).detainPlayer(anyInt(), any());
	}
}
