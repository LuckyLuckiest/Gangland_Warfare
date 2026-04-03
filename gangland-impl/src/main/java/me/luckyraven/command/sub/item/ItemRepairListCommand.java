package me.luckyraven.command.sub.item;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.gadget.repair.material.RepairMaterialManager;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Map;

class ItemRepairListCommand extends SubArgument {

	private final Gangland gangland;

	ItemRepairListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			RepairMaterialManager materialManager = gangland.getInitializer()
			                                                .getRepairManager()
			                                                .getMaterialManager();
			Map<String, RepairMaterial> materials = materialManager.getAllMaterials();

			sender.sendMessage(GanglandChatUtil.commandMessage("List of repair items"));

			Iterator<Map.Entry<String, RepairMaterial>> iterator = materials.entrySet().iterator();
			StringBuilder                               builder  = new StringBuilder();

			while (iterator.hasNext()) {
				Map.Entry<String, RepairMaterial> entry    = iterator.next();
				RepairMaterial                    material = entry.getValue();

				builder.append("&b").append(material.getId());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(GanglandChatUtil.color(builder.toString()));
		};
	}

}
