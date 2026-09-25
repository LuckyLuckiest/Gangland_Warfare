package org.luckyraven.gangland.gadget.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins gi=54 (P3): the "amount" sub-argument's {@code NumberFormatException} handler in
 * {@code CarGiveCommand}/{@code JetpackGiveCommand}/{@code GrappleGiveCommand} sent
 * {@code Messages.MUST_BE_NUMBERS.toString()} raw, leaving the {@code %command%} token in the
 * {@code Errors.Must_Be_Numbers} template unresolved — the sent message read literally "%command% must be
 * numbers." instead of naming the bad input, unlike {@code GangAllyAcceptCommand}'s working
 * {@code .replace("%command%", value)} pattern.
 */
@DisplayName("Gadget give commands — amount argument names the bad token (gi=54)")
class GiveCommandNumberMessageTest {

	@BeforeAll
	static void bootstrap() throws Exception {
		BukkitRegistryFixture.install();

		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		// Errors.Must_Be_Numbers routes through GanglandChatUtil.errorMessage -> color(), which unconditionally
		// substitutes %money_symbol% and NPEs if Settings was never initialized. A single reflective write is
		// far cheaper here than pulling gangland-impl's FileManager-backed Settings fixture across modules.
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");

		MessageProvider provider = mock(MessageProvider.class);
		when(provider.getString("Errors.Must_Be_Numbers")).thenReturn("&a%command% &7must be numbers.");
		Messages.init(provider);
	}

	/** Walks the single-child "name" -> "amount" chain every one of these give commands builds. */
	private static Argument amountArgument(Argument command) {
		Argument name = command.getNode().getChildren().get(0).getData();
		return name.getNode().getChildren().get(0).getData();
	}

	@Test
	@DisplayName("CarGiveCommand: non-numeric amount names the bad token")
	void carGiveCommand_nonNumericAmount_namesToken() {
		JavaPlugin           plugin      = mock(JavaPlugin.class);
		Tree<Argument>       tree        = new Tree<>();
		Argument             parent      = new Argument(plugin, "car", tree);
		tree.add(parent.getNode());
		UserManager<Player>  userManager = mock(UserManager.class);
		CarAddon             carAddon    = mock(CarAddon.class);

		CarGiveCommand giveCommand = new CarGiveCommand(plugin, tree, parent, userManager, carAddon);

		Player       player = mock(Player.class);
		User<Player> user   = mock(User.class);
		when(userManager.getUser(player)).thenReturn(user);

		String[] args = {"give", "car", "sportscar", "not-a-number"};
		amountArgument(giveCommand).getAction().accept(null, player, args);

		var captor = forClass(String.class);
		verify(user).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("not-a-number"), "message must name the bad token");
		assertFalse(captor.getValue().contains("%command%"), "the %command% placeholder must be resolved");
	}

	@Test
	@DisplayName("JetpackGiveCommand: non-numeric amount names the bad token")
	void jetpackGiveCommand_nonNumericAmount_namesToken() {
		JavaPlugin       plugin       = mock(JavaPlugin.class);
		Tree<Argument>   tree         = new Tree<>();
		Argument         parent       = new Argument(plugin, "jetpack", tree);
		tree.add(parent.getNode());
		JetpackAddon     jetpackAddon = mock(JetpackAddon.class);
		JetpackMessages  messages     = mock(JetpackMessages.class);

		JetpackGiveCommand giveCommand = new JetpackGiveCommand(plugin, tree, parent, jetpackAddon, messages);

		Player player = mock(Player.class);

		String[] args = {"give", "jetpack", "standard", "not-a-number"};
		amountArgument(giveCommand).getAction().accept(null, player, args);

		var captor = forClass(String.class);
		verify(player).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("not-a-number"), "message must name the bad token");
		assertFalse(captor.getValue().contains("%command%"), "the %command% placeholder must be resolved");
	}

	@Test
	@DisplayName("GrappleGiveCommand: non-numeric amount names the bad token")
	void grappleGiveCommand_nonNumericAmount_namesToken() {
		JavaPlugin      plugin       = mock(JavaPlugin.class);
		Tree<Argument>  tree         = new Tree<>();
		Argument        parent       = new Argument(plugin, "grapple", tree);
		tree.add(parent.getNode());
		GrappleAddon    grappleAddon = mock(GrappleAddon.class);
		GrappleMessages messages     = mock(GrappleMessages.class);

		GrappleGiveCommand giveCommand = new GrappleGiveCommand(plugin, tree, parent, grappleAddon, messages);

		Player player = mock(Player.class);

		String[] args = {"give", "grapple", "standard", "not-a-number"};
		amountArgument(giveCommand).getAction().accept(null, player, args);

		var captor = forClass(String.class);
		verify(player).sendMessage(captor.capture());
		assertTrue(captor.getValue().contains("not-a-number"), "message must name the bad token");
		assertFalse(captor.getValue().contains("%command%"), "the %command% placeholder must be resolved");
	}
}
