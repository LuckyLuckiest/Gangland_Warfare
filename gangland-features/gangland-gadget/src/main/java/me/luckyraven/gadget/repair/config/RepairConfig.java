package me.luckyraven.gadget.repair.config;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Getter
@Builder
public class RepairConfig {

	@NotNull
	private final Map<String, RepairMaterialData> repairMaterials;


	public static RepairConfig fromProvider(@NotNull RepairConfigProvider provider) {
		return RepairConfig.builder()
		                   .repairMaterials(provider.getRepairMaterials())
		                   .build();
	}
}
