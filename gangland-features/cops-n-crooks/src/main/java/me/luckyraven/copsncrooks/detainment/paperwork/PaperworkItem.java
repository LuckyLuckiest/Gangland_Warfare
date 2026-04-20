package me.luckyraven.copsncrooks.detainment.paperwork;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.copsncrooks.detainment.message.DetainmentMessageContract;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * "Jail Paperwork" item given to jailed players. Right-clicking it opens the {@code PaperworkView} GUI where the player
 * can pay bail, attempt a bribe, or serve out their sentence. Marked with a {@link PersistentDataContainer} key so
 * {@link PaperworkItemFactory#isPaperwork(ItemStack)} can recognize it even if the display name / material changes.
 */
public final class PaperworkItem implements PaperworkItemFactory {

	public static final String MARKER_KEY = "detainment_paperwork";

	private final NamespacedKey             markerKey;
	private final DetainmentMessageContract messages;

	public PaperworkItem(JavaPlugin plugin, DetainmentMessageContract messages) {
		this.markerKey = new NamespacedKey(plugin, MARKER_KEY);
		this.messages  = messages;
	}

	@Override
	public ItemStack create() {
		ItemStack   material = XMaterial.BOOK.parseItem();
		ItemBuilder builder  = new ItemBuilder(material != null ? material : new ItemStack(Material.BOOK));
		builder.setDisplayName(messages.paperworkItemName());
		builder.setLore(messages.paperworkItemLore());

		ItemStack stack = builder.build();
		ItemMeta  meta  = stack.getItemMeta();
		if (meta != null) {
			meta.getPersistentDataContainer().set(markerKey, PersistentDataType.INTEGER, 1);
			stack.setItemMeta(meta);
		}
		return stack;
	}

	@Override
	public boolean isPaperwork(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return false;
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return false;
		return meta.getPersistentDataContainer().has(markerKey, PersistentDataType.INTEGER);
	}
}
