package org.luckyraven.gangland.sign;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.ItemConverter;
import org.luckyraven.keystone.item.ItemConverterRegistry;
import org.luckyraven.keystone.item.ItemKind;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.item.ItemSerializer;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.keystone.item.nbt.ItemNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.item.nbt.NbtType;
import org.luckyraven.keystone.item.spi.ItemDefinitions;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Red-first pin for A2 risk 5 (gangland-0.9.0.md T-G1): deleting {@code weaponSimilarityChecker}
 * ({@code AbstractWeaponTradeSign.java:60-68}, removed with the weapon module in group B) without a pin would let
 * T-G2's replacement — {@code BuySign}/{@code SellSign} switching their similarity check from
 * {@link ItemStack#isSimilar} to {@link ItemDefinitions#sameDefinition} — silently mis-stack traded items if the
 * round trip were wrong.
 *
 * <p>Proves two things: the general contract (two stacks built from the same definition string compare equal, two
 * from different strings don't), and the throwable-UUID determinism rule the deleted checker implemented
 * ({@code UUID.nameUUIDFromBytes("throwable:" + name)}). A fake {@code throwable} kind is registered locally —
 * gangland-impl carries no Bartizan dependency, so the real weapon vocabulary is unavailable here — whose converter
 * stamps both the deterministic name tag and a random per-instance tag (standing in for a weapon's per-instance
 * random state, e.g. durability). This asserts the <em>observable consequence</em> (two freshly built throwables of
 * the same name are {@code sameDefinition}), not the UUID value itself, per the task's own "Watch out".
 *
 * <p><b>First-run result, recorded honestly per the "verify every new test is genuinely red" rule:</b> the very
 * first draft of this test used {@code keystone-testkit}'s {@link org.luckyraven.keystone.testkit.RecordingNbtAccessor}
 * and came back genuinely red — but for a fixture reason, not a production one: that accessor stores every tag in
 * one {@code Map<String,Object>} keyed by tag name only, ignoring which {@link ItemStack} it belongs to (fine for a
 * single-stack-at-a-time test such as {@code CarNbtIdentityTest}; wrong here, where two independently-built stacks
 * must carry independent tag state at once). {@link PerStackNbtAccessor} below is the minimal in-test double that
 * keys by stack identity instead, so the test now genuinely exercises {@link ItemDefinitions#sameDefinition}'s own
 * (already-correct, Keystone-shipped) contract rather than a test-double artifact — per TESTING.md §8, production
 * code is never changed to make a test pass, and neither is a test's fixture bug worth reporting as a code bug.
 */
@DisplayName("ItemDefinitions.sameDefinition — trade sign similarity pin (T-G1)")
class ItemDefinitionSimilarityTest {

	private static final String THROWABLE_NAME_TAG     = "throwable_name";
	private static final String THROWABLE_INSTANCE_TAG = "throwable_instance";

	private ItemConverterRegistry  converters;
	private ItemSerializerRegistry serializers;
	private ItemParser             parser;

	@BeforeEach
	void setUp() {
		NbtBridge.install(new PerStackNbtAccessor());

		converters = new ItemConverterRegistry();
		serializers = new ItemSerializerRegistry();
		parser = new ItemParser(converters);

		ItemKind throwableKind = ItemKind.of("throwable");

		// Reproduces the deleted weaponSimilarityChecker: a deterministic id derived from the name (so two builds of
		// the same throwable describe identically), plus a random per-build tag standing in for the per-instance
		// state (durability, uses, …) that makes ItemStack.isSimilar unreliable for this comparison.
		ItemConverter throwableConverter = (type, modifier, attributes) -> {
			ItemStack stack = new ItemStack(Material.STICK);
			UUID      deterministic = UUID.nameUUIDFromBytes(("throwable:" + modifier).getBytes());
			new ItemBuilder(stack).addTag(THROWABLE_NAME_TAG, modifier)
			                      .addTag("throwable_uuid", deterministic)
			                      .addTag(THROWABLE_INSTANCE_TAG, UUID.randomUUID().toString());
			return stack;
		};
		converters.register("throwable", throwableConverter);

		ItemSerializer throwableSerializer = new ItemSerializer() {
			@Override
			public ItemKind kind() {
				return throwableKind;
			}

			@Override
			public String extract(ItemStack stack) {
				return new ItemBuilder(stack).getStringTagData(THROWABLE_NAME_TAG);
			}
		};
		serializers.register(stack -> new ItemBuilder(stack).hasNBTTag(THROWABLE_NAME_TAG), throwableSerializer);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("two stacks built from the same definition string compare equal")
	void sameDefinitionString_comparesEqual() {
		ItemStack a = parser.parse("throwable:knife");
		ItemStack b = parser.parse("throwable:knife");

		assertTrue(ItemDefinitions.sameDefinition(serializers, a, b));
	}

	@Test
	@DisplayName("two stacks built from different definition strings do not compare equal")
	void differentDefinitionStrings_doNotCompareEqual() {
		ItemStack a = parser.parse("throwable:knife");
		ItemStack b = parser.parse("throwable:grenade");

		assertFalse(ItemDefinitions.sameDefinition(serializers, a, b));
	}

	@Test
	@DisplayName("throwable-UUID determinism: two fresh builds of the same name are sameDefinition despite each "
			+ "stamping its own random per-instance tag")
	void throwableDeterminism_twoFreshBuildsOfSameName_areSameDefinition() {
		ItemStack a = parser.parse("throwable:knife");
		ItemStack b = parser.parse("throwable:knife");

		// The per-instance random tag proves a and b are genuinely distinct objects/state, not the same stack reused
		// — exactly the case the deleted weaponSimilarityChecker had to handle for throwables.
		assertNotEquals(new ItemBuilder(a).getStringTagData(THROWABLE_INSTANCE_TAG),
		                 new ItemBuilder(b).getStringTagData(THROWABLE_INSTANCE_TAG));

		assertTrue(ItemDefinitions.sameDefinition(serializers, a, b),
		           "sameDefinition must ignore per-instance state and compare only the deterministic definition");
	}

	/**
	 * Minimal {@link ItemNbtAccessor} double keyed by stack <em>identity</em>, unlike {@code keystone-testkit}'s
	 * {@code RecordingNbtAccessor}, which keys by tag name alone and so cannot keep two independently-built stacks'
	 * tag state apart — exactly what this test needs. See the class javadoc's "First-run result" note.
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
