package org.luckyraven.gangland.gang.combat;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wolf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AttackerResolver - the player behind a damaging entity")
class AttackerResolverTest {

	private final Player player = mock(Player.class);

	@Test
	@DisplayName("a player is its own attacker")
	void player() {
		assertSame(player, AttackerResolver.resolve(player));
	}

	@Test
	@DisplayName("a projectile resolves to its player shooter, a skeleton arrow to nobody")
	void projectile() {
		Arrow mine = mock(Arrow.class);
		when(mine.getShooter()).thenReturn(player);
		Arrow skeletons = mock(Arrow.class);
		when(skeletons.getShooter()).thenReturn(mock(Skeleton.class));

		assertSame(player, AttackerResolver.resolve(mine));
		assertNull(AttackerResolver.resolve(skeletons));
	}

	@Test
	@DisplayName("a tamed wolf resolves to its owner, a wild one to nobody")
	void wolf() {
		Wolf tame = mock(Wolf.class);
		when(tame.getOwner()).thenReturn(player);

		assertSame(player, AttackerResolver.resolve(tame));
		assertNull(AttackerResolver.resolve(mock(Wolf.class)));
	}

	@Test
	@DisplayName("lit TNT resolves to the player who lit it")
	void tnt() {
		TNTPrimed lit = mock(TNTPrimed.class);
		when(lit.getSource()).thenReturn(player);
		TNTPrimed dispenser = mock(TNTPrimed.class);
		when(dispenser.getSource()).thenReturn(mock(Entity.class));

		assertSame(player, AttackerResolver.resolve(lit));
		assertNull(AttackerResolver.resolve(dispenser));
		assertNull(AttackerResolver.resolve(null));
	}
}
