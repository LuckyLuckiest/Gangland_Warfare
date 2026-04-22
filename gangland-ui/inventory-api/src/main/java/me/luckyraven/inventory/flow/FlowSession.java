package me.luckyraven.inventory.flow;

/**
 * Marker interface for the typed payload a {@link MultiPanelInventory} carries across panel switches. Feature modules
 * implement this on their own session classes (e.g. {@code TraderFlowSession}, {@code BankerFlowSession}) so the host
 * is parameterised with a concrete session type.
 */
public interface FlowSession { }
