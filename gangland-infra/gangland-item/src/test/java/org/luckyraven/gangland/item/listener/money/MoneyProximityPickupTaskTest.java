package org.luckyraven.gangland.item.listener.money;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Full-inventory proximity pickup (D-W2-33): the same bag-aware gate as the event pickup, never a direct deposit. */
@DisplayName("MoneyProximityPickupTask - full-inventory pickup goes through depositPickup")
class MoneyProximityPickupTaskTest {

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
	private MoneyProximityPickupTask task;
	private Player              player;
	private Item                entity;
	private PerStackNbtAccessor accessor;

	@BeforeEach
	void setUp() throws InvalidConfigurationException {
		accessor = new PerStackNbtAccessor();
		NbtBridge.install(accessor);
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.loadFromString(MONEY_YAML);
		addon = new MoneyAddon();
		addon.load(configuration);
		addon.setEnabled(true);

		deposits = mock(MoneyDepositService.class);
		when(deposits.resolvePlaceholders(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
		task   = new MoneyProximityPickupTask(addon, deposits);
		player = mock(Player.class);
		entity = mock(Item.class);
		when(entity.isValid()).thenReturn(true);
		when(entity.isDead()).thenReturn(false);
		ItemStack bills = MoneyItemFactory.build(addon.getVariation("small"), 100, deposits, 3);
		when(entity.getItemStack()).thenReturn(bills);
		when(player.getNearbyEntities(1.5, 1.5, 1.5)).thenReturn(List.of(entity));
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("depositPickup true: the item is removed and no direct deposit happens")
	void acceptedPickupRemovesItemWithoutDirectDeposit() {
		when(deposits.depositPickup(any(), anyDouble(), any())).thenReturn(true);

		task.scanAndDeposit(player);

		verify(deposits).depositPickup(eq(player), eq(300D), eq("small"));
		verify(entity).remove();
		verify(deposits, never()).deposit(any(Player.class), anyDouble(), anyString());
	}

	@Test
	@DisplayName("depositPickup false: the item stays on the ground and nothing is deposited")
	void refusedPickupLeavesItem() {
		when(deposits.depositPickup(any(), anyDouble(), any())).thenReturn(false);

		task.scanAndDeposit(player);

		verify(deposits).depositPickup(eq(player), eq(300D), eq("small"));
		verify(entity, never()).remove();
		verify(deposits, never()).deposit(any(Player.class), anyDouble(), anyString());
	}

	@Test
	@DisplayName("T-348 (C-43): a bill tagged copsncrooks:hot_cash is left to the normal pickup path, never put in the wallet")
	void hotBillIsSkipped() {
		ItemStack bills = MoneyItemFactory.build(addon.getVariation("small"), 100, deposits, 3);
		org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
		org.bukkit.persistence.PersistentDataContainer pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
		when(meta.getPersistentDataContainer()).thenReturn(pdc);
		when(pdc.has(new org.bukkit.NamespacedKey("copsncrooks", "hot_cash"),
		             org.bukkit.persistence.PersistentDataType.BYTE)).thenReturn(true);
		ItemStack hot = new ItemStack(bills.getType(), 3) {
			@Override
			public org.bukkit.inventory.meta.ItemMeta getItemMeta() {
				return meta;
			}
		};
		PerStackNbtAccessor tags = accessor;
		for (String key : List.of(org.luckyraven.gangland.item.money.MoneyItemUtil.MARKER_TAG,
		                          org.luckyraven.gangland.item.money.MoneyItemUtil.VARIATION_TAG,
		                          org.luckyraven.gangland.item.money.MoneyItemUtil.AMOUNT_TAG)) {
			tags.put(hot, key, tags.get(bills, key));
		}
		when(entity.getItemStack()).thenReturn(hot);
		when(deposits.depositPickup(any(), anyDouble(), any())).thenReturn(true);

		task.scanAndDeposit(player);

		verify(deposits, never()).depositPickup(any(), anyDouble(), any());
		verify(entity, never()).remove();
	}

}
