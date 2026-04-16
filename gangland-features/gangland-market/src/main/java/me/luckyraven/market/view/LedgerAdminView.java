package me.luckyraven.market.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.market.ledger.LedgerQuery;
import me.luckyraven.market.ledger.TransactionLedger;
import me.luckyraven.market.ledger.TransactionRecord;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Admin-facing ledger GUI. Each row is one {@link TransactionRecord}: player head icon with date, direction, item id,
 * quantity, unit price, total, and a small chip marking market-linked vs black-market rows.
 */
@RequiredArgsConstructor
public final class LedgerAdminView {

	private static final int INVENTORY_SIZE = 54;
	private static final int ROWS_PER_PAGE  = 45;
	private static final int SLOT_PREV      = 45;
	private static final int SLOT_INFO      = 49;
	private static final int SLOT_NEXT      = 53;

	private final JavaPlugin        plugin;
	private final TransactionLedger ledger;

	public void open(Player admin) {
		open(admin, 0);
	}

	public void open(Player admin, int page) {
		List<TransactionRecord> records = ledger.query(
				new LedgerQuery(null, null, null, null, ROWS_PER_PAGE * (page + 1) + 1, page * ROWS_PER_PAGE, true));

		InventoryHandler handler = new InventoryHandler(plugin, "§8Market Ledger §7(page " + (page + 1) + ")",
		                                                INVENTORY_SIZE, admin);

		int rendered = Math.min(ROWS_PER_PAGE, records.size());
		for (int i = 0; i < rendered; i++) {
			renderRow(handler, i, records.get(i));
		}
		for (int i = rendered; i < ROWS_PER_PAGE; i++) {
			handler.setItem(i, new ItemBuilder(paneOf(XMaterial.BLACK_STAINED_GLASS_PANE)).setDisplayName(" "), false,
			                (p, inv, b) -> { });
		}

		boolean hasNext = records.size() > ROWS_PER_PAGE;
		renderNavigation(handler, page, hasNext);

		handler.open(admin);
	}

	private void renderRow(InventoryHandler handler, int slot, TransactionRecord record) {
		LocalDateTime time       = LocalDateTime.ofInstant(record.timestamp(), ZoneId.systemDefault());
		String        dot        = record.marketLinked() ? "&a●" : "&8●";
		String        playerName = nameOf(record);

		Material    icon    = record.marketLinked() ? Material.GREEN_DYE : Material.GRAY_DYE;
		ItemBuilder builder = new ItemBuilder(new ItemStack(icon));
		builder.setDisplayName(dot + " &f" + record.direction() + " &7" + record.quantity() + "x &e" + record.itemId())
		       .setLore("&7Player: &f" + playerName,
		                "&7Unit: &e" + String.format("%.2f", record.unitPrice()),
		                "&7Total: &6" + String.format("%.2f", record.total()),
		                "&7Time: &f" + time.toLocalDate() + " " + time
								.toLocalTime().withNano(0),
		                record.marketLinked() ? "&8[market-linked]" : "&8[black market]");
		handler.setItem(slot, builder, false, (p, inv, b) -> { });
	}

	private void renderNavigation(InventoryHandler handler, int page, boolean hasNext) {
		ItemBuilder prev = new ItemBuilder(paneOf(XMaterial.LIME_STAINED_GLASS_PANE)).setDisplayName("&a◄ Previous");
		handler.setItem(SLOT_PREV, prev, false,
		                (p, inv, b) -> { if (page > 0) open(p, page - 1); });

		ItemBuilder info = new ItemBuilder(new ItemStack(Material.WRITABLE_BOOK)).setDisplayName("&6Ledger")
		                                                                         .setLore("&7Page: &e" + (page + 1),
		                                                                                  "&7Most recent first.", " ",
		                                                                                  "&8Ledger rows are kept forever.");
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		ItemBuilder next = new ItemBuilder(
				paneOf(hasNext ? XMaterial.LIME_STAINED_GLASS_PANE : XMaterial.GRAY_STAINED_GLASS_PANE))
				.setDisplayName(hasNext ? "&aNext ►" : "&8No more pages");
		handler.setItem(SLOT_NEXT, next, false,
		                (p, inv, b) -> { if (hasNext) open(p, page + 1); });

		for (int slot = ROWS_PER_PAGE; slot < INVENTORY_SIZE; slot++) {
			if (slot == SLOT_PREV || slot == SLOT_INFO || slot == SLOT_NEXT) {
				continue;
			}
			handler.setItem(slot, new ItemBuilder(paneOf(XMaterial.GRAY_STAINED_GLASS_PANE)).setDisplayName(" "), false,
			                (p, inv, b) -> { });
		}
	}

	private String nameOf(TransactionRecord record) {
		try {
			String resolved = Bukkit.getOfflinePlayer(record.playerId()).getName();
			return resolved != null ? resolved : record.playerId().toString().substring(0, 8);
		} catch (Exception e) {
			return record.playerId().toString().substring(0, 8);
		}
	}

	private ItemStack paneOf(XMaterial preferred) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
	}
}
