package org.luckyraven.gangland.command.sub.gang;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.click.ClickContext;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.color.Color;
import org.luckyraven.keystone.color.ColorUtil;
import org.luckyraven.keystone.color.MaterialType;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.gangland.util.GanglandChatUtil;

class GangColorCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final InventoryService    inventoryService;

	protected GangColorCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                           UserManager<Player> userManager, MemberManager memberManager,
	                           GangManager gangManager, InventoryService inventoryService) {
		super(gangland, "color", tree, parent);

		this.gangland         = gangland;
		this.userManager      = userManager;
		this.memberManager    = memberManager;
		this.gangManager      = gangManager;
		this.inventoryService = inventoryService;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			// GR-03: the gang colour picker opened for every member.
			if (!GangPermissions.allows(memberManager.getMember(player.getUniqueId()), player,
			                            GangPermissions.COLOR)) {
				user.sendMessage(Messages.COMMAND_NO_PERM.toString());
				return;
			}

			displayColors(player);
		};
	}

	private void displayColors(Player player) {
		String title = "&5&lChoose a color";

		ChestMenuBuilder colorMenu = ChestMenu.builder(inventoryService).title(title).rows(6);

		int row = 2, column = 2;
		for (Color color : Color.values()) {
			String colorName = color.name();
			String colorCode = color.getColorCode();

			MaterialType type         = MaterialType.WOOL;
			String       materialName = type.name();

			Material material = ColorUtil.getMaterialByColor(colorName, materialName);

			if (material == null) return;

			String name = colorCode + GanglandChatUtil.capitalize(colorName.toLowerCase().replace('_', ' ')) + " " +
			              GanglandChatUtil.capitalize(materialName.toLowerCase().replace('_', ' '));

			ItemStack previewItem = new ItemBuilder(material).setDisplayName(name).build();

			int slot = (row - 1) * 9 + (column - 1);

			colorMenu.slot(slot, ItemComponent.of(previewItem).onAnyClick(ctx -> {
				Player player1 = ctx.player();
				String title2  = "&4&lAre you sure?";

				ChestMenuBuilder confirmMenu = ChestMenu.builder(inventoryService).title(title2).rows(6);
				confirmMenu.slot(22, ItemComponent.of(previewItem));

				// aroundSlot's 8-slot ring around slot 22 (a 3x3 block minus the center), ported directly — a fresh
				// ChestMenuBuilder has no stale prior-render state to skip, unlike the old InventoryUtil.aroundSlot's
				// occupied-slot check (same simplification already established for the trader/banker Panel port, WS2 G4).
				Material  mat      = ColorUtil.getMaterialByColor(colorName, MaterialType.STAINED_GLASS_PANE.name());
				ItemStack ringItem = new ItemBuilder(mat).setDisplayName(null).build();
				for (int ringSlot : new int[] {12, 13, 14, 21, 23, 30, 31, 32}) {
					confirmMenu.slot(ringSlot, ItemComponent.of(ringItem));
				}

				confirmMenu.slot(49, ItemComponent.of(new ItemBuilder(XMaterial.GREEN_CONCRETE.get())
						                                       .setDisplayName("&aConfirm"))
				                                  .onAnyClick(confirmCtx -> {
					Player player2 = confirmCtx.player();
					User<Player> user = userManager.getUser(player2);

					if (user == null) return;

					Gang gang = gangManager.getGang(user.getGangId());
					// save the data in gang
					gang.setColor(colorName);

					// inform player
					String colorSelected = GanglandChatUtil.color(
							colorCode + GanglandChatUtil.capitalize(
									colorName.toLowerCase().replace('_', ' ')));
					player2.sendMessage(Messages.GANG_COLOR_SET.toString()
					                                           .replace("%color%", colorSelected));

					confirmCtx.closeMenu();
				}));

				confirmMenu.fill(FillComponent.of(materialOf(InventoryBuilder.DEFAULT_FILL_ITEM))
				                              .name(InventoryBuilder.DEFAULT_FILL_NAME));

				confirmMenu.build().open(player1);
			}));

			if (column % 8 == 0) {
				column = 2;
				++row;
			} else ++column;
		}

		colorMenu.slot((6 - 1) * 9, ItemComponent.of(new ItemBuilder(XMaterial.RED_CONCRETE.get())
				                                              .setDisplayName("&4Exit"))
		                                         .onAnyClick(ClickContext::closeMenu));

		// Preserves a pre-existing quirk: the old Fill(Settings.getInventoryFillName(),
		// Settings.getInventoryLineName()) call paired the fill NAME getter with the line NAME getter (not a
		// material id) in the material slot — materialOf(...) falls back to BLACK_STAINED_GLASS_PANE either way
		// since both default to " ". Carried forward verbatim (re-point, not redesign; not in the LS-30/LS-31
		// carve-out).
		colorMenu.border(BorderComponent.of(materialOf(InventoryBuilder.DEFAULT_LINE_NAME))
		                                .name(InventoryBuilder.DEFAULT_FILL_NAME));

		colorMenu.build().open(player);
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

}
