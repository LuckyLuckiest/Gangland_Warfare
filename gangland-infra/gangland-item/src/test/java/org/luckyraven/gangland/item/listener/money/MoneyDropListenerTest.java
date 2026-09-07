package org.luckyraven.gangland.item.listener.money;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.money.MoneyDropClassifier;
import org.luckyraven.gangland.item.money.MoneyDropContext;
import org.luckyraven.gangland.item.money.MoneyItemUtil;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the money-conservation rule of {@link MoneyDropListener}.
 *
 * <p>Observation #6 (users-levels-economy-bank.md) / US-06: on a player death the listener rolled an amount
 * (optionally topped up by a fraction of the dead player's balance), built the cash item and dropped it —
 * without ever debiting the dead player. Every player death therefore minted currency equal to the whole
 * dropped amount, on top of the separate death tax {@code PlayerDeathListener} charges from the same balance.
 *
 * <p>A player-death drop must now <em>move</em> money: debit first through
 * {@link MoneyDepositService#withdraw(Player, double)} and drop only what came back. Mob, cop and civilian
 * drops stay a genuine currency source by design, which the last test guards against an over-eager fix.
 */
@DisplayName("MoneyDropListener - player deaths move money, they never mint it")
class MoneyDropListenerTest {

	private static final String MONEY_YAML = """
			Money:
			  Default_Variation: 'small'
			  Drop_Sources:
			    PLAYER:
			      Enabled: true
			      Scale_With_Balance: false
			      Balance_Fraction: 0.0
			      Variations:
			        small:
			          weight: 1
			    MOB:
			      Enabled: true
			      Scale_With_Balance: false
			      Variations:
			        small:
			          weight: 1
			Variations:
			  small:
			    Material: PAPER
			    Display_Name: '&aCash'
			    Min: 100
			    Max: 100
			""";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// MoneyItemFactory -> ItemBuilder.setDisplayName reaches Bukkit.getItemFactory(); Material.isAir()
		// reaches Registry (documentation/TESTING.md section 4a).
		BukkitRegistryFixture.install();
	}

	private MoneyAddon          addon;
	private MoneyDepositService depositService;
	private MoneyDropClassifier classifier;
	private MoneyDropListener   listener;

	private World    world;
	private Location location;

	@BeforeEach
	void setUp() throws InvalidConfigurationException {
		NbtBridge.install(new PerStackNbtAccessor());

		YamlConfiguration configuration = new YamlConfiguration();
		configuration.loadFromString(MONEY_YAML);

		addon = new MoneyAddon();
		addon.load(configuration);
		addon.setEnabled(true);

		depositService = mock(MoneyDepositService.class);
		// MoneyItemFactory runs the display name and every lore line through the placeholder pipeline.
		when(depositService.resolvePlaceholders(any(), any()))
				.thenAnswer(invocation -> invocation.getArgument(1));

		classifier = mock(MoneyDropClassifier.class);
		listener   = new MoneyDropListener(addon, depositService, classifier);

		world    = mock(World.class);
		location = mock(Location.class);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private Player deadPlayer() {
		Player player = mock(Player.class);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(location);
		return player;
	}

	private PlayerDeathEvent playerDeath(Player player) {
		PlayerDeathEvent event = mock(PlayerDeathEvent.class);
		when(event.getEntity()).thenReturn(player);
		return event;
	}

	private ItemStack capturedDrop() {
		ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
		verify(world).dropItemNaturally(eq(location), captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("US-06: the rolled amount is debited from the dead player before the cash item spawns")
	void playerDeath_debitsTheDropFromTheDeadPlayer() {
		Player player = deadPlayer();
		when(depositService.withdraw(player, 100D)).thenReturn(100D);

		listener.onPlayerDeath(playerDeath(player));

		verify(depositService).withdraw(player, 100D);

		ItemStack dropped = capturedDrop();
		assertTrue(MoneyItemUtil.isMoneyItem(dropped));
		assertEquals(100, MoneyItemUtil.readAmount(dropped),
		             "the dropped cash must equal what was taken off the balance, so the death is money-neutral");
	}

	@Test
	@DisplayName("US-06: a player who cannot cover the roll drops only what was actually debited")
	void playerDeath_partialBalance_dropsOnlyWhatWasDebited() {
		Player player = deadPlayer();
		when(depositService.withdraw(player, 100D)).thenReturn(40D);

		listener.onPlayerDeath(playerDeath(player));

		assertEquals(40, MoneyItemUtil.readAmount(capturedDrop()),
		             "the drop is clamped to the debit, otherwise the difference is minted");
	}

	@Test
	@DisplayName("US-06: a broke player drops nothing at all")
	void playerDeath_brokePlayer_dropsNothing() {
		Player player = deadPlayer();
		when(depositService.withdraw(player, 100D)).thenReturn(0D);

		listener.onPlayerDeath(playerDeath(player));

		verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
	}

	@Test
	@DisplayName("mob deaths remain a genuine currency source and debit nobody")
	void mobDeath_dropsWithoutDebitingAnyone() {
		LivingEntity zombie = mock(LivingEntity.class);
		when(zombie.getWorld()).thenReturn(world);
		when(zombie.getLocation()).thenReturn(location);
		when(classifier.classify(zombie)).thenReturn(MoneyDropContext.MOB);

		EntityDeathEvent event = mock(EntityDeathEvent.class);
		when(event.getEntity()).thenReturn(zombie);

		listener.onEntityDeath(event);

		verify(depositService, never()).withdraw(any(), anyDouble());
		assertEquals(100, MoneyItemUtil.readAmount(capturedDrop()));
	}

}
