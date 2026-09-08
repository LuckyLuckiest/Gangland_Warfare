package org.luckyraven.gangland.file.configuration.shop;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.item.ItemConverterRegistry;
import org.luckyraven.keystone.item.ItemKind;
import org.luckyraven.keystone.item.ItemSerializer;
import org.luckyraven.keystone.item.ItemSerializerRegistry;
import org.luckyraven.keystone.util.ChatUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Red-first pin for review finding B4 (gangland-0.9.0.md T-R4): {@link GanglandShopDisplayResolver} put
 * {@code ItemDefinitions.pristine} ahead of the live stack's own display name. That is wrong for a kind whose
 * converter round trip is non-deterministic — a money item's {@code MoneyItemSerializer.extract} keeps only the
 * variation id, so rebuilding it factory-fresh re-rolls the amount into the display name. This test stands up a
 * throwaway "cash" vocabulary (a fake serializer/converter pair, same pattern as
 * {@code ItemDefinitionSimilarityTest}'s throwable kind — gangland-impl carries no real Money-item-with-ItemMeta
 * fixture usable outside a live server) whose converter deliberately returns a stack with a <em>different</em>
 * display name than the live one, standing in for the re-roll.
 */
@DisplayName("GanglandShopDisplayResolver.cleanDisplayName — live name before pristine (T-R4)")
class GanglandShopDisplayResolverTest {

	@Test
	@DisplayName("a money item's display name is the live stack's name, not a re-rolled pristine rebuild")
	void moneyItem_prefersLiveDisplayName_overPristineRebuild() {
		ItemStack liveStack = mock(ItemStack.class);
		ItemMeta  liveMeta  = mock(ItemMeta.class);
		when(liveStack.hasItemMeta()).thenReturn(true);
		when(liveStack.getItemMeta()).thenReturn(liveMeta);
		when(liveMeta.hasDisplayName()).thenReturn(true);
		when(liveMeta.getDisplayName()).thenReturn("&aCash: $73");

		// Stands in for MoneyConverter re-rolling a fresh random amount into a rebuilt stack's name.
		ItemStack pristineStack = mock(ItemStack.class);
		ItemMeta  pristineMeta  = mock(ItemMeta.class);
		when(pristineStack.hasItemMeta()).thenReturn(true);
		when(pristineStack.getItemMeta()).thenReturn(pristineMeta);
		when(pristineMeta.hasDisplayName()).thenReturn(true);
		when(pristineMeta.getDisplayName()).thenReturn("&aCash: $999");

		ItemSerializerRegistry serializers = new ItemSerializerRegistry();
		serializers.register(stack -> stack == liveStack, new ItemSerializer() {
			@Override
			public ItemKind kind() {
				return ItemKind.of("cash");
			}

			@Override
			public String extract(ItemStack stack) {
				return "roll";
			}
		});

		ItemConverterRegistry converters = new ItemConverterRegistry();
		converters.register("cash", (type, modifier, attributes) -> pristineStack);

		GanglandShopDisplayResolver resolver = new GanglandShopDisplayResolver(serializers, converters);

		assertEquals(ChatUtil.color("&aCash: $73"), resolver.cleanDisplayName(liveStack),
		             "the live stack's own display name must win — pristine would report a re-rolled amount");
	}

}
