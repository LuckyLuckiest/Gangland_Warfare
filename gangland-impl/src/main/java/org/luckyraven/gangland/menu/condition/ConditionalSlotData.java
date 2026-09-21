package org.luckyraven.gangland.menu.condition;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.inventory.click.ClickContext;
import org.luckyraven.keystone.inventory.registry.MenuOpener;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.List;

/**
 * Represents conditional data for a slot that can have True/False branches
 */
@Getter
public class ConditionalSlotData {

	private final SlotCondition condition;
	private final BranchData    trueData;
	private final BranchData    falseData;

	public ConditionalSlotData(SlotCondition condition, BranchData trueData, BranchData falseData) {
		this.condition = condition;
		this.trueData  = trueData;
		this.falseData = falseData;
	}

	/**
	 * Resolves which branch to use based on condition evaluation
	 */
	public BranchData resolve(Player player, ConditionEvaluator evaluator) {
		return condition.evaluate(player, evaluator) ? trueData : falseData;
	}

	/**
	 * Represents a click action which can be command, inventory, or anvil. {@code opener} is the {@link MenuOpener}
	 * captured at parse time (WS2 G3: replaces the old {@code InventoryOpener}) — Gangland's own menus are opened
	 * through it directly rather than through {@code ClickContext.openMenu}/a registered {@code MenuRegistry}
	 * factory, because the paginated core menus need a real {@code Player} at menu-BUILD time (to fetch that
	 * player's filtered item-source entries before {@code PagedRegion.render} bakes them into explicit slots) and a
	 * zero-arg {@code Supplier<Menu>} factory cannot supply one; using {@code MenuOpener} uniformly for every core
	 * menu (paginated or not) keeps the open path consistent instead of half on the registry, half not.
	 */
	public interface ClickAction {
		void execute(ClickContext ctx, MenuOpener opener);
	}

	/**
	 * Represents a branch (True or False) which can have its own nested conditions
	 */
	@Getter
	public static class BranchData {
		private final ItemBuilder         item;
		private final String              name;
		private final List<String>        lore;
		private final boolean             clickable;
		private final boolean             draggable;
		private final ClickAction         clickAction;
		private final ClickAction         rightClickAction;
		private final ConditionalSlotData nestedCondition;

		public BranchData(ItemBuilder item, String name, List<String> lore, boolean clickable, boolean draggable,
		                  @Nullable ClickAction clickAction, @Nullable ClickAction rightClickAction,
		                  @Nullable ConditionalSlotData nestedCondition) {
			this.item             = item;
			this.name             = name;
			this.lore             = lore;
			this.clickable        = clickable;
			this.draggable        = draggable;
			this.clickAction      = clickAction;
			this.rightClickAction = rightClickAction;
			this.nestedCondition  = nestedCondition;
		}

		/**
		 * Resolves the final data, following nested conditions if present
		 */
		public BranchData resolveFinal(Player player, ConditionEvaluator evaluator) {
			if (nestedCondition != null) {
				return nestedCondition.resolve(player, evaluator).resolveFinal(player, evaluator);
			}
			return this;
		}
	}

	public record CommandAction(String command) implements ClickAction {
		@Override
		public void execute(ClickContext ctx, MenuOpener opener) {
			String cmd = command.startsWith("/") ? command.substring(1) : command;
			ctx.player().performCommand(cmd);
		}
	}

	public record InventoryAction(String inventoryName) implements ClickAction {
		@Override
		public void execute(ClickContext ctx, MenuOpener opener) {
			opener.open(ctx.player(), inventoryName);
		}
	}

	public record AnvilAction(String title, String text, String successCommand) implements ClickAction {
		@Override
		public void execute(ClickContext ctx, MenuOpener opener) {
			// Will be handled by InventoryBuilder with AnvilGUI
		}
	}
}
