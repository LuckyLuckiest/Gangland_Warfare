package me.luckyraven.util.color;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public final class ColorUtil {

	private static final Map<String, String>   colorCodeMap = new HashMap<>();
	private static final Map<String, Material> materialMap  = new HashMap<>();

	static {
		for (Color color : Color.values()) {
			String colorName = color.name();

			// color code map
			colorCodeMap.put(colorName.toLowerCase(), color.getColorCode());

			// material color map
			for (MaterialType type : MaterialType.values()) {
				String materialName = type.name();

				materialMap.put(colorName.toLowerCase() + "_" + materialName.toLowerCase(),
								XMaterial.valueOf(colorName + "_" + materialName).get());
			}
		}
	}

	private ColorUtil() { }

	public static Material getMaterialByColor(String colorName, String materialName) {
		return materialMap.get(colorName.toLowerCase() + "_" + materialName.toLowerCase());
	}

	public static String getColorCode(String colorName) {
		return colorCodeMap.get(colorName.toLowerCase());
	}

}
