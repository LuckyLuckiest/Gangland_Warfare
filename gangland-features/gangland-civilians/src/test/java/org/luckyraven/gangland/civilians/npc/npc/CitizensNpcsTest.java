package org.luckyraven.gangland.civilians.npc.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CitizensNpcs.create - PLAYER NPCs never fetch the name-based Mojang skin (no skin-arrival respawn)")
class CitizensNpcsTest {

	@Test
	@DisplayName("a PLAYER NPC gets its default skin fetch disabled")
	void create_player_disablesDefaultSkinFetch() {
		NPC       npc  = mock(NPC.class);
		SkinTrait skin = mock(SkinTrait.class);
		doReturn(skin).when(npc).getOrAddTrait(SkinTrait.class);

		assertSame(npc, create(EntityType.PLAYER, npc));
		verify(skin).setFetchDefaultSkin(false);
	}

	@Test
	@DisplayName("a non-PLAYER NPC gets no skin trait")
	void create_nonPlayer_leavesSkinAlone() {
		NPC npc = mock(NPC.class);

		assertSame(npc, create(EntityType.VILLAGER, npc));
		verify(npc, never()).getOrAddTrait(any());
	}

	private static NPC create(EntityType type, NPC npc) {
		NPCRegistry registry = mock(NPCRegistry.class);
		when(registry.createNPC(type, "Officer")).thenReturn(npc);
		try (MockedStatic<CitizensAPI> citizens = mockStatic(CitizensAPI.class)) {
			citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
			return CitizensNpcs.create(type, "Officer");
		}
	}
}
