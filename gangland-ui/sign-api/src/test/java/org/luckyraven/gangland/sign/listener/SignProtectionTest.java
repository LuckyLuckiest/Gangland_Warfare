package org.luckyraven.gangland.sign.listener;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignPermissions;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteractionService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link SignProtection}. Before docket LS-19 there was no {@code BlockBreakEvent} handler at all, so any
 * player who could break blocks could delete another player's shop sign.
 *
 * <p>Observation #19 (lootchests-signs-waypoints.md), docket LS-19.
 */
@DisplayName("SignProtection - a plugin sign may only be broken by a permitted player")
class SignProtectionTest {

	private static final String[] BUY_SIGN = {"[BUY]", "stone", "64", "10"};

	private SignTypeRegistry       registry;
	private SignInformation        information;
	private SignProtection         listener;

	@BeforeEach
	void setUp() {
		SignInteractionService service = mock(SignInteractionService.class);
		registry    = mock(SignTypeRegistry.class);
		information = mock(SignInformation.class);

		when(service.getRegistry()).thenReturn(registry);
		when(information.getSignNoPermission()).thenReturn("no permission");

		listener = new SignProtection(service, information);
	}

	private static BlockBreakEvent event(Player player, BlockState state) {
		Block block = mock(Block.class);
		when(block.getState()).thenReturn(state);

		BlockBreakEvent event = mock(BlockBreakEvent.class);
		when(event.getBlock()).thenReturn(block);
		when(event.getPlayer()).thenReturn(player);
		return event;
	}

	private static Sign sign(String[] lines) {
		Sign sign = mock(Sign.class);
		when(sign.getLines()).thenReturn(lines);
		return sign;
	}

	@Test
	@DisplayName("breaking a plugin sign without the node is cancelled - the LS-19 defect")
	void withoutPermission_breakIsCancelled() {
		when(registry.findByLine("[BUY]")).thenReturn(Optional.of(mock(SignTypeDefinition.class)));

		Player player = mock(Player.class);
		when(player.hasPermission(SignPermissions.BREAK)).thenReturn(false);

		BlockBreakEvent event = event(player, sign(BUY_SIGN));

		listener.onSignBreak(event);

		verify(event).setCancelled(true);
		verify(player).sendMessage("no permission");
	}

	@Test
	void withPermission_breakGoesThrough() {
		when(registry.findByLine("[BUY]")).thenReturn(Optional.of(mock(SignTypeDefinition.class)));

		Player player = mock(Player.class);
		when(player.hasPermission(SignPermissions.BREAK)).thenReturn(true);

		BlockBreakEvent event = event(player, sign(BUY_SIGN));

		listener.onSignBreak(event);

		verify(event, never()).setCancelled(true);
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("an ordinary sign is not protected")
	void unknownSignType_isNotProtected() {
		when(registry.findByLine("hello")).thenReturn(Optional.empty());

		Player player = mock(Player.class);
		when(player.hasPermission(SignPermissions.BREAK)).thenReturn(false);

		BlockBreakEvent event = event(player, sign(new String[]{"hello", "", "", ""}));

		listener.onSignBreak(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("a non-sign block is ignored before the registry is ever consulted")
	void nonSignBlock_isIgnored() {
		Player player = mock(Player.class);

		BlockBreakEvent event = event(player, mock(BlockState.class));

		listener.onSignBreak(event);

		verify(event, never()).setCancelled(true);
		verify(registry, never()).findByLine(anyString());
	}

}
