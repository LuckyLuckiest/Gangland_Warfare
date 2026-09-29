package org.luckyraven.gangland.healthbars.bar;

import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.trait.HologramTrait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bar line against a {@link HologramTrait} whose lines are a real list: the bar goes in at index 0, directly
 * under the cop's callsign line (lines stack upward), is updated in place and is removed at full health.
 */
@DisplayName("NpcHealthBar - one bar line under the callsign (phase H13)")
class NpcHealthBarTest {

	private final List<String>        lines    = new ArrayList<>();
	private final Map<String, Object> metadata = new HashMap<>();
	private       HologramTrait       hologram;
	private       MetadataStore       data;

	@BeforeEach
	void setUp() {
		hologram = mock(HologramTrait.class);
		when(hologram.getLines()).thenAnswer(invocation -> List.copyOf(lines));
		doAnswer(invocation -> {
			lines.add(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(hologram).insertLine(anyInt(), anyString());
		doAnswer(invocation -> lines.set(invocation.getArgument(0), invocation.getArgument(1))).when(hologram)
		                                                                                    .setLine(anyInt(),
		                                                                                             anyString());
		doAnswer(invocation -> lines.remove((int) invocation.getArgument(0))).when(hologram).removeLine(anyInt());

		data = mock(MetadataStore.class);
		doAnswer(invocation -> metadata.put(invocation.getArgument(0), invocation.getArgument(1))).when(data)
		                                                                                     .set(anyString(),
		                                                                                          org.mockito.ArgumentMatchers.any());
		when(data.has(anyString())).thenAnswer(invocation -> metadata.containsKey(invocation.<String>getArgument(0)));
		when(data.get(anyString())).thenAnswer(invocation -> metadata.get(invocation.<String>getArgument(0)));
		doAnswer(invocation -> metadata.remove(invocation.<String>getArgument(0))).when(data).remove(anyString());
	}

	@Test
	@DisplayName("the first hit inserts the bar under the callsign line")
	void firstHit_insertsUnderCallsign() {
		lines.add("Officer Bob #1592");

		NpcHealthBar.show(hologram, data, "bar1");

		assertEquals(List.of("bar1", "Officer Bob #1592"), lines);
	}

	@Test
	@DisplayName("a later hit updates the bar in place, no second insert (insertLine respawns every line)")
	void secondHit_setsLineOnly() {
		lines.add("Officer Bob #1592");
		NpcHealthBar.show(hologram, data, "bar1");

		NpcHealthBar.show(hologram, data, "bar2");
		NpcHealthBar.show(hologram, data, "bar2");

		assertEquals(List.of("bar2", "Officer Bob #1592"), lines);
		verify(hologram).insertLine(0, "bar1");
		verify(hologram).setLine(0, "bar2");
	}

	@Test
	@DisplayName("full health removes the bar and leaves the callsign; nothing to remove is a no-op")
	void full_removesBarOnly() {
		lines.add("Officer Bob #1592");
		NpcHealthBar.show(hologram, data, "bar1");

		NpcHealthBar.show(hologram, data, null);
		NpcHealthBar.show(hologram, data, null);

		assertEquals(List.of("Officer Bob #1592"), lines);
		assertFalse(metadata.containsKey(NpcHealthBar.BAR_KEY));
		verify(hologram).removeLine(0);
	}

	@Test
	@DisplayName("an NPC with no hologram lines (civilian) gets the bar as its only line")
	void noCallsign_barAlone() {
		NpcHealthBar.show(hologram, data, "bar1");

		assertEquals(List.of("bar1"), lines);
		assertTrue(metadata.containsKey(NpcHealthBar.BAR_KEY));
	}

	@Test
	@DisplayName("full health on an NPC that never showed a bar touches no line")
	void neverShown_full_untouched() {
		lines.add("Officer Bob #1592");

		NpcHealthBar.show(hologram, data, null);

		verify(hologram, never()).removeLine(anyInt());
		assertEquals(List.of("Officer Bob #1592"), lines);
	}
}
