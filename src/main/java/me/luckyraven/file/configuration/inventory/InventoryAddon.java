package me.luckyraven.file.configuration.inventory;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.inventory.InventoryBuilder;
import me.luckyraven.file.FileHandler;
import me.luckyraven.util.InventoryUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerEvent;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryAddon {

	@Getter
	private static final Map<String, InventoryBuilder>                inventories     = new HashMap<>();
	private static final Map<String, Class<? extends PlayerEvent>>    playerEvents    = new HashMap<>();
	private static final Map<String, Class<? extends InventoryEvent>> inventoryEvents = new HashMap<>();

	static {
		// initialize player events
//		loadEventSubclasses(playerEvents, PlayerEvent.class);

		// initialize inventory events
		inventoryEvents.put("OnClick", InventoryClickEvent.class);
		inventoryEvents.put("OnInteract", InventoryInteractEvent.class);
		inventoryEvents.put("OnClose", InventoryCloseEvent.class);
		inventoryEvents.put("OnInventory", InventoryEvent.class);
	}

	public static void registerInventory(Gangland gangland, FileHandler fileHandler) {
		FileConfiguration config = fileHandler.getFileConfiguration();
		final String      name   = fileHandler.getName().toLowerCase();

		String information = "Information.";
		String slots       = "Slots.";

		// information section
		String tempName = config.getString(information + "Name");
		if (tempName == null || tempName.isEmpty()) tempName = name.substring(0, name.lastIndexOf('.'));
		String displayName = config.getString(information + "Display_Name");
		int    size        = config.getInt(information + "Size");
		String type        = config.getString(information + "Type");

		String openCommand    = config.getString(information + "Open.Command");
		String openEvent      = config.getString(information + "Open.Event");
		String openPermission = config.getString(information + "Open.Permission");

		boolean       configFill       = config.getBoolean(information + "Configuration.Fill");
		boolean       configBorder     = config.getBoolean(information + "Configuration.Border");
		List<Integer> configVertical   = config.getIntegerList(information + "Configuration.Line.Vertical");
		List<Integer> configHorizontal = config.getIntegerList(information + "Configuration.Line.Horizontal");

		InventoryHandler inventoryHandler = new InventoryHandler(gangland, displayName, size, tempName, true);

		// slots section
		for (int slot = 0; slot < inventoryHandler.getSize(); ++slot) {
			String sectionStr = slots + slot + ".";
			ConfigurationSection section = config.getConfigurationSection(
					sectionStr.substring(0, sectionStr.length() - 1));
			if (section == null) continue;

			String       item      = section.getString("Item");
			String       itemName  = section.getString("Name");
			List<String> lore      = section.getStringList("Lore");
			boolean      enchanted = section.getBoolean("Enchanted");
			boolean      draggable = section.getBoolean("Draggable");

			for (String event : inventoryEvents.keySet()) {
				String eventSectionStr = sectionStr + event + ".";
				ConfigurationSection eventSection = config.getConfigurationSection(
						eventSectionStr.substring(0, eventSectionStr.length() - 1));
				if (eventSection == null) continue;

				// so far, there is support for clickable events
				if (!inventoryEvents.get(event).equals(InventoryClickEvent.class)) return;

				String slotCommand    = eventSection.getString("Command");
				String slotInventory  = eventSection.getString("Inventory");
				String slotPermission = eventSection.getString("Permission");

				inventoryHandler.setItem(slot, XMaterial.valueOf(item).parseMaterial(), itemName, lore, enchanted,
				                         draggable, (player, inventory, itemBuilder) -> {
							if (slotPermission != null) {
								if (!player.hasPermission(slotPermission)) return;
							}

							if (slotCommand != null) {
								player.performCommand(slotCommand.replace('/', '\0'));
							}
							if (slotInventory != null) {
								InventoryBuilder builder = inventories.get(slotInventory);

								if (builder == null) return;
								builder.getInventoryHandler().open(player);
							}
						});

				break;
			}

			if (!inventoryHandler.itemOccupied(slot)) {
				inventoryHandler.setItem(slot, XMaterial.valueOf(item).parseMaterial(), itemName, lore, enchanted,
				                         draggable);
			}
		}

		if (configFill) InventoryUtil.fillInventory(inventoryHandler);
		else if (configBorder) InventoryUtil.createBoarder(inventoryHandler);

		if (!configVertical.isEmpty()) {
			for (int column : configVertical)
				InventoryUtil.verticalLine(inventoryHandler, column);
		}

		if (!configHorizontal.isEmpty()) {
			for (int row : configHorizontal)
				InventoryUtil.horizontalLine(inventoryHandler, row);
		}

		InventoryBuilder builder = new InventoryBuilder(inventoryHandler);

		// open the inventory according to these states
		InventoryBuilder.State state = null;
		String                 value = null;
		if (openCommand != null) {
			state = InventoryBuilder.State.COMMAND;
			value = openCommand;
		} else if (openEvent != null) {
			state = InventoryBuilder.State.EVENT;
			value = openEvent;
		}

		builder.addOpen(state, value, openPermission);

		inventories.put(tempName, builder);
	}

	private static <T extends Event> void loadEventSubclasses(Map<String, Class<? extends T>> events,
	                                                          Class<T> initialClass) {
		Package eventPackage = initialClass.getPackage();
		String  packageName  = eventPackage.getName();
		String  packagePath  = packageName.replace(".", "/");

		ClassLoader classLoader = initialClass.getClassLoader();

		URL packageUrl = classLoader.getResource(packagePath);
		if (packageUrl == null) return;

		String packagePathStr = packageUrl.getPath();
		File   packageDir     = new File(packagePathStr);
		File[] files          = packageDir.listFiles();
		if (!packageDir.isDirectory() || files == null) return;

		for (File file : files) {
			String fileName = file.getName();
			if (!fileName.endsWith(".class")) continue;

			// add the package directory and remove the .class
			String className = packageName + "." + fileName.substring(0, fileName.length() - 6);
			try {
				Class<?> eventClass = Class.forName(className);
				if (initialClass.isAssignableFrom(eventClass)) {
					String eventName        = eventClass.getSimpleName().replace("Event", "");
					String initialClassName = initialClass.getSimpleName().replace("Event", "");

					StringBuilder builder = new StringBuilder("On");
					String changedName =
							initialClassName.length() == eventName.length() ? eventName : eventName.substring(
									initialClassName.length() - 1, eventName.length() - 1);
					builder.append(changedName);

					events.put(builder.toString(), eventClass.asSubclass(initialClass));
				}
			} catch (ClassNotFoundException exception) {
				Gangland.getLog4jLogger().error("The class was not found", exception);
			}
		}
	}

}
