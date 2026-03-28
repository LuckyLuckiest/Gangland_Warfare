package me.luckyraven.inventory.part;

/**
 * The inventory item line record to be used to indicate the name and material, used for filling a line inside the
 * inventory.
 *
 * @param name the material name
 * @param material the material itself
 */
public record Fill(String name, String material) { }
