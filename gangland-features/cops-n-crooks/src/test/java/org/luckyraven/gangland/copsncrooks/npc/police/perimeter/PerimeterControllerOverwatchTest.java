package org.luckyraven.gangland.copsncrooks.npc.police.perimeter;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.OverwatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PerimeterSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-O1 on {@link PerimeterController}: from {@code Cops.Overwatch.Min_Level} (and with overwatch enabled) the perimeter
 * leaves the Overwatch role to the overwatch and posts the rest; below it, or with overwatch off, nothing changes.
 */
@DisplayName("PerimeterController - Marksman skipped while overwatch owns him")
class PerimeterControllerOverwatchTest {

	private final UUID              playerId = UUID.randomUUID();
	private final Player            player   = mock(Player.class);
	private final World             world    = PostRingTest.flatWorld();
	private final Location          centre   = new Location(world, 0, 64, 0);
	private final CopManager        manager  = mock(CopManager.class);
	private final CopRadio          radio    = mock(CopRadio.class);
	private final CopGroup          group    = mock(CopGroup.class);
	private final List<CopNpc>      cops     = new ArrayList<>();
	private final CopConfigProvider provider = mock(CopConfigProvider.class);

	private OverwatchSettings overwatch = OverwatchSettings.DEFAULT;

	private final PerimeterController controller;

	PerimeterControllerOverwatchTest() {
		when(player.getUniqueId()).thenReturn(playerId);
		when(manager.groupOf(playerId)).thenReturn(group);
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(mock(NpcSquad.class));
		when(provider.getPerimeterSettings()).thenReturn(PerimeterSettings.DEFAULT);
		when(provider.getOverwatchSettings()).thenAnswer(i -> overwatch);
		when(radio.placeOf(any())).thenReturn("the plaza");
		controller = new PerimeterController(() -> provider, manager, radio, () -> 1_000_000L);
	}

	private CopNpc chaser(String role, double x) {
		CopNpc cop = mock(CopNpc.class);
		AtomicReference<CopState> current = new AtomicReference<>(CopState.PURSUING);
		CopRole copRole = new CopRole(role, role, NpcFanPlacement.ANY, null, null, 1.0, null, 0, 0.0, 1.0, 0, null, 0.0,
		                              0.0, false, false);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(cop.getRole()).thenReturn(copRole);
		when(cop.getEntity()).thenReturn(entity);
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenAnswer(i -> current.get());
		doAnswer(i -> {
			current.set(i.getArgument(0));
			return null;
		}).when(cop).transitionTo(any());
		cops.add(cop);
		return cop;
	}

	@Test
	@DisplayName("at four stars the Marksman is skipped: the Defender and the nearest other cop take the posts")
	void marksmanSkippedAtFourStars() {
		CopNpc assault   = chaser("Assault", 1);
		CopNpc commander = chaser("Commander", 2);
		CopNpc marksman  = chaser("Marksman", 30);
		CopNpc defender  = chaser("Defender", 40);

		controller.start(player, centre, 90, 4);

		verify(marksman, never()).holdPost(any());
		verify(marksman, never()).transitionTo(CopState.POSTED);
		verify(defender).holdPost(any());
		verify(assault).holdPost(any());
		verify(commander, never()).holdPost(any());
	}

	@Test
	@DisplayName("at three stars the Marksman is still posted first")
	void marksmanStillFirstAtThreeStars() {
		CopNpc assault  = chaser("Assault", 1);
		CopNpc marksman = chaser("Marksman", 30);
		CopNpc defender = chaser("Defender", 40);

		controller.start(player, centre, 90, 3);

		verify(marksman).holdPost(any());
		verify(defender).holdPost(any());
		verify(assault, never()).holdPost(any());
	}

	@Test
	@DisplayName("with overwatch disabled the Marksman is posted at four stars as before")
	void disabledOverwatchChangesNothing() {
		overwatch = new OverwatchSettings(false, 4, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120);
		chaser("Assault", 1);
		CopNpc marksman = chaser("Marksman", 30);
		chaser("Defender", 40);

		controller.start(player, centre, 90, 4);

		verify(marksman).holdPost(any());
	}

	@Test
	@DisplayName("Overwatch.Min_Level 5 leaves the Marksman to the perimeter at four stars; the Role key picks who is skipped")
	void minLevelAndRoleComeFromTheOverwatchConfig() {
		overwatch = new OverwatchSettings(true, 5, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120);
		chaser("Assault", 1);
		CopNpc marksman = chaser("Marksman", 30);
		chaser("Defender", 40);

		controller.start(player, centre, 90, 4);
		verify(marksman).holdPost(any());
		assertEquals(CopState.POSTED, marksman.getCurrentState());
	}

	@Test
	@DisplayName("a renamed Overwatch.Role skips that role, not Marksman")
	void renamedRoleIsTheOneSkipped() {
		overwatch = new OverwatchSettings(true, 4, "Assault", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120);
		CopNpc assault  = chaser("Assault", 1);
		CopNpc marksman = chaser("Marksman", 30);
		CopNpc defender = chaser("Defender", 40);

		controller.start(player, centre, 90, 4);

		verify(assault, never()).holdPost(any());
		verify(marksman).holdPost(any());
		verify(defender).holdPost(any());
	}
}
