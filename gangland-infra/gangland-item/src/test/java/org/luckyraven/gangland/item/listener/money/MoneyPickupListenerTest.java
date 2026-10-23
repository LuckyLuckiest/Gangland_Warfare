package org.luckyraven.gangland.item.listener.money;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.money.MoneyItemFactory;
import org.luckyraven.gangland.item.support.PerStackNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MoneyPickupListener - the deposit service decides whether the bill is taken")
class MoneyPickupListenerTest {

	private static final String MONEY_YAML = """
			Money:
			  Default_Variation: 'small'
			Variations:
			  small:
			    Material: PAPER
			    Display_Name: '&aCash'
			    Min: 100
			    Max: 100
			""";

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	private MoneyAddon          addon;
	private MoneyDepositService deposits;
	private MoneyPickupListener listener;
	private Player              player;
	private Item                entity;

	@BeforeEach
	void setUp() throws InvalidConfigurationException {
		NbtBridge.install(new PerStackNbtAccessor());
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.loadFromString(MONEY_YAML);
		addon = new MoneyAddon();
		addon.load(configuration);
		addon.setEnabled(true);

		deposits = mock(MoneyDepositService.class);
		when(deposits.resolvePlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
		listener = new MoneyPickupListener(addon, deposits);
		player   = mock(Player.class);
		entity   = mock(Item.class);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private EntityPickupItemEvent pickup(LivingEntity who, ItemStack stack) {
		when(entity.getItemStack()).thenReturn(stack);
		EntityPickupItemEvent event = mock(EntityPickupItemEvent.class);
		when(event.getEntity()).thenReturn(who);
		when(event.getItem()).thenReturn(entity);
		return event;
	}

	private ItemStack bills(int count) {
		return MoneyItemFactory.build(addon.getVariation("small"), 100, deposits, count);
	}

	@Test
	@DisplayName("depositPickup true: event cancelled, item removed, asked for perItem x count with the variation")
	void walletPathRemovesItemAndDeposits() {
		when(deposits.depositPickup(any(), anyDouble(), any())).thenReturn(true);
		EntityPickupItemEvent event = pickup(player, bills(3));

		listener.onMoneyPickup(event);

		verify(event).setCancelled(true);
		verify(entity).remove();
		verify(deposits).depositPickup(eq(player), eq(300D), eq("small"));
		verify(deposits, never()).deposit(any(Player.class), anyDouble(), anyString());
	}

	@Test
	@DisplayName("depositPickup false: event cancelled, the item stays on the ground")
	void refusedPickupLeavesItem() {
		when(deposits.depositPickup(any(), anyDouble(), any())).thenReturn(false);
		EntityPickupItemEvent event = pickup(player, bills(1));

		listener.onMoneyPickup(event);

		verify(event).setCancelled(true);
		verify(entity, never()).remove();
	}

	@Test
	@DisplayName("a plain item, a non-player or a disabled addon is untouched")
	void nonMoneyAndDisabledUntouched() {
		EntityPickupItemEvent plain = pickup(player, new ItemStack(Material.STONE));
		listener.onMoneyPickup(plain);
		verify(plain, never()).setCancelled(true);

		EntityPickupItemEvent mob = pickup(mock(LivingEntity.class), bills(1));
		listener.onMoneyPickup(mob);
		verify(mob, never()).setCancelled(true);

		addon.setEnabled(false);
		EntityPickupItemEvent off = pickup(player, bills(1));
		listener.onMoneyPickup(off);
		verify(off, never()).setCancelled(true);
		verify(deposits, never()).depositPickup(any(), anyDouble(), any());
	}

}
