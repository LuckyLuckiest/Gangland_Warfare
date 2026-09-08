package org.luckyraven.gangland.sign.type.trade;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.ItemPredicates;
import org.luckyraven.gangland.item.serializer.MoneyItemSerializer;
import org.luckyraven.gangland.item.serializer.UniqueItemSerializer;
import org.luckyraven.gangland.item.unique.UniqueItemKeys;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.keystone.item.MaterialItemSerializer;
import org.luckyraven.keystone.item.nbt.ItemNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.item.nbt.NbtType;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Red-first pin for review finding B1 (gangland-0.9.0.md T-R1): {@link BaseTradeSign#sameTradeDefinition} must not
 * let the {@code material} catch-all tier collapse to bare-material identity — a {@code [SELL] DIAMOND_SWORD} sign
 * must not take a Sharpness V sword along with a plain one, and two unique-tagged stacks with different runtime
 * state must still trade as the same item.
 *
 * <p>Wired exactly like {@code ItemConfig}'s production registry: {@code UniqueItemSerializer}/{@code MoneyItemSerializer}
 * at the default priority, {@code MaterialItemSerializer} at {@link ItemSerializerRegistry#CATCH_ALL_PRIORITY}.
 *
 * <p>The enchanted-vs-plain case stubs {@link ItemStack#isSimilar} directly on a {@link org.mockito.Mockito#spy}
 * of a real {@code ItemStack} rather than building genuine {@code ItemMeta} — this module's unit tests have no
 * server-backed item factory (see {@code BukkitRegistryFixture}'s own "out of scope" note for meta-bearing stacks),
 * and the point of this test is that {@code sameTradeDefinition} consults {@code isSimilar} for the material tier,
 * not that Bukkit's own {@code isSimilar} is correct.
 */
@DisplayName("BaseTradeSign.sameTradeDefinition — material catch-all must not collapse (T-R1)")
class BaseTradeSignSimilarityTest {

	private ItemSerializerRegistry serializers;

	@BeforeAll
	static void installServer() {
		// MaterialItemSerializer.extract calls Material.isAir(), which on the 1.21 API resolves through
		// Registry.BLOCK and NPEs with no server installed — see BukkitRegistryFixture's own javadoc.
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		NbtBridge.install(new PerStackNbtAccessor());

		serializers = new ItemSerializerRegistry();
		serializers.register(ItemPredicates.UNIQUE, new UniqueItemSerializer());
		serializers.register(ItemPredicates.MONEY, new MoneyItemSerializer());
		serializers.register(ItemPredicates.MATERIAL, new MaterialItemSerializer(), ItemSerializerRegistry.CATCH_ALL_PRIORITY);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("an enchanted diamond sword is NOT the same trade item as a plain one")
	void materialCatchAll_enchantedVsPlain_notSame() {
		ItemStack plain     = spy(new ItemStack(Material.DIAMOND_SWORD));
		ItemStack enchanted = spy(new ItemStack(Material.DIAMOND_SWORD));

		// Both describe as "material:diamond_sword" (the serializer ignores meta); only isSimilar tells them apart,
		// exactly as it would for a real Sharpness V sword vs. a plain one.
		doReturn(false).when(plain).isSimilar(enchanted);
		doReturn(false).when(enchanted).isSimilar(plain);

		assertFalse(BaseTradeSign.sameTradeDefinition(serializers, plain, enchanted));
	}

	@Test
	@DisplayName("two unique-tagged stacks with different runtime state ARE the same trade item")
	void uniqueTier_differentRuntimeState_same() {
		ItemStack a = new ItemStack(Material.STICK);
		ItemStack b = new ItemStack(Material.STICK);

		new ItemBuilder(a).addTag(UniqueItemKeys.UNIQUE_ITEM_KEY, "widget");
		new ItemBuilder(b).addTag(UniqueItemKeys.UNIQUE_ITEM_KEY, "widget");

		// Simulates differing runtime state (durability, uses, …) that must not affect unique-tier identity.
		new ItemBuilder(a).addTag("uses", 3);
		new ItemBuilder(b).addTag("uses", 9);

		assertTrue(BaseTradeSign.sameTradeDefinition(serializers, a, b));
	}

	/**
	 * Minimal {@link ItemNbtAccessor} double keyed by stack <em>identity</em> — see
	 * {@code ItemDefinitionSimilarityTest.PerStackNbtAccessor} for the same fixture and its rationale.
	 */
	private static final class PerStackNbtAccessor implements ItemNbtAccessor {

		private final Map<ItemStack, Map<String, Object>> storage = new IdentityHashMap<>();

		private Map<String, Object> tagsOf(ItemStack stack) {
			return storage.computeIfAbsent(stack, s -> new HashMap<>());
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public boolean has(ItemStack stack, String tag) {
			return tagsOf(stack).containsKey(tag);
		}

		@Override
		@Nullable
		public Object get(ItemStack stack, String tag) {
			return tagsOf(stack).get(tag);
		}

		@Override
		@Nullable
		public String getString(ItemStack stack, String tag) {
			Object value = tagsOf(stack).get(tag);
			return value == null ? null : String.valueOf(value);
		}

		@Override
		public int getInt(ItemStack stack, String tag) {
			Object value = tagsOf(stack).get(tag);
			return value instanceof Number number ? number.intValue() : 0;
		}

		@Override
		public void set(ItemStack stack, String tag, NbtType type, @Nullable Object value) {
			tagsOf(stack).put(tag, value);
		}

		@Override
		public void remove(ItemStack stack, String tag) {
			tagsOf(stack).remove(tag);
		}

		@Override
		@Nullable
		public String describe(ItemStack stack) {
			return "recorded" + tagsOf(stack);
		}
	}

}
