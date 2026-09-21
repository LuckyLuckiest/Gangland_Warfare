package org.luckyraven.gangland.menu;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.click.ClickContext;
import org.luckyraven.keystone.inventory.click.ClickHandler;
import org.luckyraven.keystone.inventory.component.BorderComponent;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.component.LineComponent;
import org.luckyraven.keystone.inventory.page.PageConfig;
import org.luckyraven.keystone.inventory.page.PagedRegion;
import org.luckyraven.keystone.inventory.registry.MenuOpener;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.keystone.color.ColorUtil;
import org.luckyraven.keystone.color.MaterialType;
import org.luckyraven.gangland.menu.condition.ConditionEvaluator;
import org.luckyraven.gangland.menu.condition.ConditionalSlotData;
import org.luckyraven.gangland.menu.multi.ItemSourceEntry;
import org.luckyraven.gangland.menu.multi.ItemSourceProvider;
import org.luckyraven.gangland.menu.part.ButtonTags;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.menu.part.ConditionalSlotResult;
import org.luckyraven.gangland.menu.part.Slot;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds a Keystone {@link ChestMenu} from the parsed {@link InventoryData} (WS2 G3: retargets off the deleted
 * {@code InventoryHandler}/{@code InventoryBuilder.createInventory}/{@code createMultiInventory} pair). Every menu
 * this class builds is FRESH per call — there is no cached {@code MenuRegistry} factory — because
 * {@code openInventoryForPlayer} (the one caller, via {@link org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext})
 * always has the real viewing {@link Player} in hand, so every placeholder (including the paginated menus' item
 * source, which needs the player to filter/sort per {@code FilterStore}) resolves eagerly at build time, exactly as
 * the old {@code InventoryHandler}-based builder did. This is also why menu-to-menu navigation
 * ({@code OnClick.Inventory: xyz}) goes through the {@link MenuOpener} captured at parse time
 * (see {@code menu.handler.*}) rather than {@code ClickContext.openMenu}/a registered {@code MenuRegistry} entry: a
 * registry factory is a zero-arg {@code Supplier<Menu>} with no player to build paginated content against.
 */
@CustomLog
public record InventoryBuilder(InventoryData inventoryData, String permission) {

	private static final String COLOR_TAG = "color";
	private static final String HEAD_TAG  = "head";
	private static final String DATA_TAG  = "data";

	/** Matches the old {@code MultiInventoryNavigation.buttonClickSound}'s {@code XSound.BLOCK_WOODEN_BUTTON_CLICK_ON},
	 *  routed through Keystone's {@link SoundEffect} instead of a raw {@code XSound}/{@code Sound} call.
	 *  Package-visible: reused by {@link SimplePagedMenu}'s own next/previous/home buttons. */
	static final SoundEffect NAV_CLICK_SOUND =
			new SoundEffect(SoundEffect.SoundType.VANILLA, "BLOCK_WOODEN_BUTTON_CLICK_ON", 1F, 1F);

	/**
	 * Builds a single (non-paginated) menu. Mirrors the old {@code createInventory}'s slot/line/border/fill
	 * composition; Keystone's own {@link ChestMenuBuilder#build()} applies the same priority order (border, then
	 * lines, then explicit slots override both) the old {@code InventoryUtil} calls approximated by skipping
	 * already-occupied slots.
	 */
	public ChestMenu createMenu(InventoryService inventoryService, JavaPlugin plugin, Placeholder placeholder,
	                            Player player, Fill fill, Fill line, ConditionEvaluator evaluator, MenuOpener opener) {
		String title = placeholder.convert(player, inventoryData.getDisplayName());
		int    rows  = Math.max(1, Math.min(6, inventoryData.getSize() / 9));

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title(title).rows(rows);
		if (permission != null) builder.permission(permission);

		for (Slot slot : inventoryData.getSlots()) {
			var result = slot.getConditionalResult(player, evaluator);
			ItemBuilder rawItem = result.item();
			if (rawItem == null) continue;

			ItemStack resolvedStack = resolveItemStack(rawItem, placeholder, player);

			ItemComponent component = ItemComponent.of(resolvedStack);
			if (result.draggable()) component.interactive(true);

			ClickHandler leftClick = resolveClickHandler(result.clickAction(), result.rawClickAction(), plugin,
			                                             placeholder, player, opener);
			ClickHandler rightClick = resolveRightClickHandler(slot, result, plugin, placeholder, player, opener);

			// The old InventoryClickHandler (listener/InventoryClickHandler.java:48-62) only branched on
			// event.isRightClick() before falling through to the left action for every other click type (middle,
			// shift, drop, number-key...) — no shipped core YAML ever declares OnRightClick, so every button's
			// left action used to answer any click. onAnyClick reproduces that fall-through; a real right handler
			// still gets its own dedicated binding (matching the old right-click branch, which ran ONLY the right
			// action and never the left one on an actual right-click).
			if (leftClick != null) {
				if (rightClick != null) {
					component.onLeftClick(leftClick);
				} else {
					component.onAnyClick(leftClick);
				}
			}
			if (rightClick != null) component.onRightClick(rightClick);

			builder.slot(slot.getSlot(), component);
		}

		applyDecoration(builder, fill, line);

		return builder.build();
	}

	/**
	 * Builds a paginated menu (the old {@code Type: multi-inventory}) — one fixed-size {@link ChestMenu}, its
	 * interior grid rendered via {@link PagedRegion} for {@code page}, replacing the old model of a chained
	 * linked-list of differently-titled Bukkit inventories ({@code MultiInventory}/{@code MultiInventoryCreation}).
	 * Next/previous buttons rebuild a fresh menu for the target page and swap it into the currently-open one via
	 * {@link ChestMenu#adoptComponentsFrom} — an in-place render, no close/reopen flicker.
	 */
	public ChestMenu createPagedMenu(InventoryService inventoryService, JavaPlugin plugin, Placeholder placeholder,
	                                 Player player, ConditionEvaluator evaluator, Fill fill, ButtonTags buttonTags,
	                                 ItemSourceProvider itemSourceProvider, MenuOpener opener, int page) {
		Map<Integer, Slot> staticItems = inventoryData.getStaticItems();
		boolean             hasStatic  = staticItems != null && !staticItems.isEmpty();

		List<ItemSourceEntry> sourceEntries = itemSourceProvider.getEntries(player, inventoryData.getItemSource());
		List<PagedRegion.Entry> entries = renderTemplateEntries(sourceEntries, placeholder, player, plugin, opener);

		int        rows       = computeRows(entries.size(), hasStatic ? staticItems.size() : 0, hasStatic);
		int        firstCol   = hasStatic ? 2 : 1;
		PageConfig pageConfig = PageConfig.forSize(rows, 1, rows - 2, firstCol, 7, entries.size());
		int        resolvedPage = Math.min(Math.max(page, 0), Math.max(0, pageConfig.pageCount() - 1));

		// The old MultiInventoryNavigation.addNavigationButtons renamed every page's title to include the
		// "[current/total]" suffix, including a single-page menu — matched here for parity.
		String title = placeholder.convert(player, inventoryData.getDisplayName())
		                          + String.format(" &8[&b%d&8/&3%d&8]&r", resolvedPage + 1, pageConfig.pageCount());

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title(title).rows(rows);
		if (permission != null) builder.permission(permission);

		if (hasStatic) {
			int size = rows * 9;
			java.util.Set<Integer> placedSlots = new java.util.HashSet<>();
			for (Map.Entry<Integer, Slot> entry : staticItems.entrySet()) {
				int  slotIndex = entry.getKey();
				Slot slot      = entry.getValue();

				// Matches the old MultiInventory.placeStaticItems: a Static_Items slot key out of range for this
				// menu's size, or one colliding with an already-placed static item, is skipped rather than
				// silently overwriting/throwing — this class additionally warns (the old code skipped silently).
				if (slotIndex < 0 || slotIndex >= size) {
					log.warn("Skipping Static_Items slot {} for inventory '{}' — out of range for a {}-row menu",
					         slotIndex, inventoryData.getName(), rows);
					continue;
				}
				if (!placedSlots.add(slotIndex)) {
					log.warn("Skipping duplicate Static_Items slot {} for inventory '{}'", slotIndex,
					         inventoryData.getName());
					continue;
				}

				ItemBuilder rawItem = slot.getItem();
				if (rawItem == null) continue;

				// The old MultiInventory model resolved static items through processItemStack (head OR data tag),
				// not the createInventory per-slot path (head only) — resolveItemStack's 4th-arg overload matches.
				ItemComponent component = ItemComponent.of(resolveItemStack(rawItem, placeholder, player, true));
				ClickHandler clickHandler = slot.getClickableSlot();
				if (clickHandler != null) component.onLeftClick(clickHandler);
				builder.slot(slotIndex, component);
			}
			// Visual separator between the static-items column and the paged entries, matching the old
			// InventoryUtil.verticalLine(multi, fill, 2, true) call (1-indexed column 2 = 0-indexed column 1).
			builder.line(LineComponent.vertical(1, materialOf(fill.material())).name(fill.name()));
		}

		PagedRegion.render(builder, pageConfig, entries, resolvedPage);

		addNavigationButtons(builder, inventoryService, plugin, placeholder, player, evaluator, fill, buttonTags,
		                     itemSourceProvider, opener, rows, resolvedPage, pageConfig.pageCount());

		// The old MultiInventoryCreation.dynamicMultiInventory always called InventoryUtil.createBoarder(multi,
		// fill) unconditionally — the paged path never consulted Configuration.Fill/Border at all, unlike the
		// single-menu path below (applyDecoration). Keep that: phone_gang_search.yml (Fill: true, Border: false)
		// must still render with a border only, not a fully filled grid.
		builder.border(BorderComponent.of(materialOf(fill.material())).name(fill.name()));

		return builder.build();
	}

	private void addNavigationButtons(ChestMenuBuilder builder, InventoryService inventoryService, JavaPlugin plugin,
	                                  Placeholder placeholder, Player player, ConditionEvaluator evaluator, Fill fill,
	                                  ButtonTags buttonTags, ItemSourceProvider itemSourceProvider, MenuOpener opener,
	                                  int rows, int page, int pageCount) {
		int size = rows * 9;

		if (page < pageCount - 1) {
			// Old addNextPageItem's lore: the page it's going TO, 1-indexed (page is 0-indexed here).
			String lore = String.format("&7(%d/%d)", page + 2, pageCount);
			builder.slot(size - 1, ItemComponent.of(headItem("&a->", buttonTags.nextPage(), lore))
			                                    .onLeftClick(ctx -> {
				var fresh = createPagedMenu(inventoryService, plugin, placeholder, player, evaluator, fill,
				                            buttonTags, itemSourceProvider, opener, page + 1);
				swapInPlace(ctx, fresh);
			}));
		}

		if (page > 0) {
			builder.slot(size - 5, ItemComponent.of(headItem("&cBack to " + placeholder.convert(player,
			                                                                                   inventoryData.getDisplayName()),
			                                                 buttonTags.homePage()))
			                                    .onLeftClick(ctx -> {
				var fresh = createPagedMenu(inventoryService, plugin, placeholder, player, evaluator, fill,
				                            buttonTags, itemSourceProvider, opener, 0);
				swapInPlace(ctx, fresh);
			}));
			// Old addPreviousPageItem's lore: the page it's going BACK to, 1-indexed (== page, since page here is
			// 0-indexed and the previous page's 1-indexed number equals the current 0-indexed page number).
			String lore = String.format("&7(%d/%d)", page, pageCount);
			builder.slot(size - 9, ItemComponent.of(headItem("&c<-", buttonTags.previousPage(), lore))
			                                    .onLeftClick(ctx -> {
				var fresh = createPagedMenu(inventoryService, plugin, placeholder, player, evaluator, fill,
				                            buttonTags, itemSourceProvider, opener, page - 1);
				swapInPlace(ctx, fresh);
			}));
		}
	}

	private static void swapInPlace(ClickContext ctx, ChestMenu fresh) {
		if (ctx.menu() instanceof ChestMenu current) {
			current.adoptComponentsFrom(fresh);
		}
		NAV_CLICK_SOUND.playSound(ctx.player());
	}

	/** Package-visible: reused by {@link SimplePagedMenu}'s own next/previous/home buttons. */
	static ItemStack headItem(String name, String base64Texture, String... lore) {
		ItemBuilder item = new ItemBuilder(Material.PLAYER_HEAD).setDisplayName(name);
		if (lore.length > 0) item.setLore(lore);
		item.customHead(base64Texture);
		return item.build();
	}

	/**
	 * Row determination ported from the old {@code MultiInventoryCreation.computeConfigForCreation}: explicit YAML
	 * size first, then static-item-driven, then item-count-driven, clamped to [3, 6].
	 */
	private int computeRows(int itemsCount, int staticItemCount, boolean hasStatic) {
		int maxColumns = hasStatic ? 6 : 7;
		int size       = inventoryData.getSize();

		int rows;
		if (size >= 18 && size % 9 == 0) {
			rows = size / 9;
		} else if (hasStatic && staticItemCount > 0) {
			rows = staticItemCount + 2;
		} else {
			rows = (int) Math.ceil((double) Math.max(itemsCount, 1) / maxColumns) + 2;
		}
		return Math.max(3, Math.min(rows, 6));
	}

	private List<PagedRegion.Entry> renderTemplateEntries(List<ItemSourceEntry> sourceEntries, Placeholder placeholder,
	                                                      Player player, JavaPlugin plugin, MenuOpener opener) {
		Slot   template        = inventoryData.getItemTemplate();
		String commandTemplate = inventoryData.getItemTemplateCommand();
		if (template == null || template.getItem() == null) return List.of();

		return sourceEntries.stream()
		                    .map(entry -> renderTemplateEntry(template, commandTemplate, entry.placeholders(),
		                                                      placeholder, player))
		                    .toList();
	}

	private PagedRegion.Entry renderTemplateEntry(Slot template, String commandTemplate, Map<String, String> entry,
	                                              Placeholder placeholder, Player player) {
		ItemBuilder source = template.getItem();
		Material    type   = source.getType();

		if (source.hasNBTTag(COLOR_TAG)) {
			String raw   = substituteEntry(source.getStringTagData(COLOR_TAG), entry);
			String value = placeholder.convert(player, raw);
			type = resolveColorMaterial(type, value);
		}

		ItemBuilder fresh = new ItemBuilder(type);

		if (source.hasNBTTag(HEAD_TAG) || source.hasNBTTag(DATA_TAG)) {
			String raw = source.hasNBTTag(HEAD_TAG) ? source.getStringTagData(HEAD_TAG) : source.getStringTagData(DATA_TAG);
			fresh.customHead(placeholder.convert(player, substituteEntry(raw, entry)));
		}

		fresh.setDisplayName(placeholder.convert(player, substituteEntry(source.getDisplayName(), entry)));
		fresh.setLore(source.getLore().stream().map(s -> placeholder.convert(player, substituteEntry(s, entry))).toList());
		applyEnchantGlint(fresh, source);

		ItemStack stack = fresh.build();

		ClickHandler onClick = null;
		if (commandTemplate != null && !commandTemplate.isEmpty()) {
			String substituted = placeholder.convert(player, substituteEntry(commandTemplate, entry));
			String cleaned     = substituted.startsWith("/") ? substituted.substring(1) : substituted;
			onClick = ctx -> ctx.player().performCommand(cleaned);
		}

		return new PagedRegion.Entry(stack, onClick);
	}

	private static String substituteEntry(String template, Map<String, String> entry) {
		if (template == null || template.isEmpty() || entry.isEmpty()) return template;
		String result = template;
		for (Map.Entry<String, String> e : entry.entrySet()) {
			result = result.replace("%" + e.getKey() + "%", e.getValue());
		}
		return result;
	}

	private void applyDecoration(ChestMenuBuilder builder, Fill fill, Fill line) {
		List<Integer> verticalLine   = inventoryData.getVerticalLine();
		List<Integer> horizontalLine = inventoryData.getHorizontalLine();

		if (verticalLine != null) {
			// YAML columns are 1-indexed (old InventoryUtil.verticalLine convention); Keystone's LineComponent is
			// 0-indexed.
			for (int l : verticalLine) builder.line(LineComponent.vertical(l - 1, materialOf(line.material())).name(line.name()));
		}
		if (horizontalLine != null) {
			for (int l : horizontalLine) builder.line(LineComponent.horizontal(l - 1, materialOf(line.material())).name(line.name()));
		}

		if (inventoryData.isBorder()) {
			builder.border(BorderComponent.of(materialOf(fill.material())).name(fill.name()));
		} else if (inventoryData.isFill()) {
			builder.fill(FillComponent.of(materialOf(fill.material())).name(fill.name()));
		}
	}

	private static Material materialOf(String name) {
		var xMaterial = XMaterial.matchXMaterial(name);
		return xMaterial.map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

	@Nullable
	private ClickHandler resolveClickHandler(@Nullable ClickHandler prebuilt,
	                                         @Nullable ConditionalSlotData.ClickAction raw,
	                                         JavaPlugin plugin, Placeholder placeholder, Player player,
	                                         MenuOpener opener) {
		if (raw instanceof ConditionalSlotData.AnvilAction anvilAction) {
			return ctx -> openAnvilInventory(plugin, placeholder, ctx.player(), anvilAction);
		}
		if (raw != null) {
			return ctx -> raw.execute(ctx, opener);
		}
		return prebuilt;
	}

	@Nullable
	private ClickHandler resolveRightClickHandler(Slot slot, ConditionalSlotResult result,
	                                              JavaPlugin plugin, Placeholder placeholder, Player player,
	                                              MenuOpener opener) {
		if (result.rawRightClickAction() instanceof ConditionalSlotData.AnvilAction anvilAction) {
			return ctx -> openAnvilInventory(plugin, placeholder, ctx.player(), anvilAction);
		}
		if (result.rawRightClickAction() != null) {
			var rawRight = result.rawRightClickAction();
			return ctx -> rawRight.execute(ctx, opener);
		}
		return slot.getRightClickSlot();
	}

	private void openAnvilInventory(JavaPlugin plugin, Placeholder placeholder, Player player,
	                                ConditionalSlotData.AnvilAction anvilAction) {
		String title          = placeholder.convert(player, anvilAction.title());
		String text           = placeholder.convert(player, anvilAction.text());
		String successCommand = anvilAction.successCommand();

		new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
			if (slot != AnvilGUI.Slot.OUTPUT) {
				return Collections.emptyList();
			}

			String output = stateSnapshot.getText();

			if (successCommand != null) {
				String command = successCommand.replace("%gangland_anvil_output%", output);
				command = placeholder.convert(player, command);

				if (command.startsWith("/")) command = command.substring(1);
				stateSnapshot.getPlayer().performCommand(command);
			}

			return List.of(AnvilGUI.ResponseAction.close());
		}).text(text).title(title).plugin(plugin).open(player);
	}

	private static ItemStack resolveItemStack(ItemBuilder item, Placeholder placeholder, Player player) {
		return resolveItemStack(item, placeholder, player, false);
	}

	/**
	 * {@code honorDataTag}: the old codebase had two different per-item resolvers — {@code createInventory}'s
	 * per-slot path (regular YAML {@code Slots}, {@code head} tag only) and {@code processItemStack} ({@code
	 * Static_Items} in a multi-inventory, {@code head} OR {@code data} — M8). Regular slots keep the head-only
	 * behavior (unchanged from the shipped YAMLs' actual rendering — e.g. {@code gang_info.yml} slot 19's {@code
	 * Data:} key was never consumed there either); the static-items call site passes {@code true} to match
	 * {@code processItemStack}.
	 */
	private static ItemStack resolveItemStack(ItemBuilder item, Placeholder placeholder, Player player,
	                                          boolean honorDataTag) {
		Material type = item.getType();

		if (item.hasNBTTag(COLOR_TAG)) {
			String value = placeholder.convert(player, item.getStringTagData(COLOR_TAG));
			type = resolveColorMaterial(type, value);
		}

		ItemBuilder newItem = new ItemBuilder(type);

		if (item.hasNBTTag(HEAD_TAG)) {
			newItem.customHead(placeholder.convert(player, item.getStringTagData(HEAD_TAG)));
		} else if (honorDataTag && item.hasNBTTag(DATA_TAG)) {
			newItem.customHead(placeholder.convert(player, item.getStringTagData(DATA_TAG)));
		}

		newItem.setDisplayName(placeholder.convert(player, item.getDisplayName()));
		newItem.setLore(item.getLore().stream().map(s -> placeholder.convert(player, s)).toList());
		applyEnchantGlint(newItem, item);

		return newItem.build();
	}

	private static Material resolveColorMaterial(Material type, String colorValue) {
		MaterialType material = MaterialType.WOOL;
		for (MaterialType materialType : MaterialType.values()) {
			if (!type.name().contains(materialType.name())) continue;
			material = materialType;
			break;
		}
		return ColorUtil.getMaterialByColor(colorValue, material.name());
	}

	private static void applyEnchantGlint(ItemBuilder target, ItemBuilder source) {
		if (!source.getEnchantments().isEmpty()) {
			target.addEnchantment(XEnchantment.UNBREAKING.get(), 1)
			      .addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
		}
	}

}
