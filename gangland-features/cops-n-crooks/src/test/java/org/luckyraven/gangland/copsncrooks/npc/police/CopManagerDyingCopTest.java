package org.luckyraven.gangland.copsncrooks.npc.police;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * B1 (phase H13): Citizens' LOW death handler despawns a killed cop with reason DEATH, after which
 * {@code getNpc().getEntity()} is null. Money drops classify the body at MONITOR, so {@link CopManager#findDyingCop}
 * must still recognise it through the Citizens registry (a field read on the body, valid while it dies).
 */
@DisplayName("CopManager - a despawned dying cop body is still recognised")
class CopManagerDyingCopTest {

	private CopManagerFixture        fx;
	private MockedStatic<NpcSupport> npcs;

	@BeforeEach
	void setUp() {
		fx   = new CopManagerFixture();
		npcs = mockStatic(NpcSupport.class);
		// no tier name: the dispatch line would colour it through the plugin's static Settings
		when(fx.provider.getTierConfig(2).displayName()).thenReturn(null);
	}

	@AfterEach
	void tearDown() {
		npcs.close();
		fx.close();
	}

	@Test
	@DisplayName("the registry resolves the body to the cop's NPC after Citizens detached the entity")
	void findDyingCop_entityDetached_matchesByRegistryIdentity() {
		Player player = fx.player(0, 0);
		fx.manager.onWantedStart(player, CopManagerFixture.wanted(2));
		CopNpc cop = fx.cop(CopState.PURSUING, 5, 5);
		fx.manager.groupFor(player.getUniqueId()).add(cop);

		NPC          npc  = cop.getNpc();
		LivingEntity body = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		when(npc.getEntity()).thenReturn(null); // despawn(DEATH) nulled the controller's entity
		NPCRegistry registry = mock(NPCRegistry.class);
		when(registry.getNPC(body)).thenReturn(npc);
		npcs.when(NpcSupport::registry).thenReturn(Optional.of(registry));

		assertSame(cop, fx.manager.findDyingCop(body));
	}

	@Test
	@DisplayName("a body that resolves to another NPC is not a cop")
	void findDyingCop_otherNpc_null() {
		Player player = fx.player(0, 0);
		fx.manager.onWantedStart(player, CopManagerFixture.wanted(2));
		CopNpc cop = fx.cop(CopState.PURSUING, 5, 5);
		fx.manager.groupFor(player.getUniqueId()).add(cop);

		LivingEntity body = mock(LivingEntity.class);
		when(body.getUniqueId()).thenReturn(UUID.randomUUID());
		NPCRegistry registry = mock(NPCRegistry.class);
		when(registry.getNPC(body)).thenReturn(mock(NPC.class));
		npcs.when(NpcSupport::registry).thenReturn(Optional.of(registry));

		assertNull(fx.manager.findDyingCop(body));
	}
}
