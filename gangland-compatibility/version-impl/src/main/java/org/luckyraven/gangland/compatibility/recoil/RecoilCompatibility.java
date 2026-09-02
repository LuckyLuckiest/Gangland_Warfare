package org.luckyraven.gangland.compatibility.recoil;

import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Fallback recoil implementation used when no per-version NMS adapter matches the running server: rotates the
 * camera through the Bukkit API instead of position packets. Per-version {@code Recoil_1_XX_RY} adapters extend
 * this and override {@link #modifyCameraRotation}.
 *
 * <p>ViaVersion is resolved through a supplier because the API becomes available only after Gangland's
 * dependency handler runs — well after this bean graph is built. Without ViaVersion the client rotation gate is
 * skipped entirely: the server floor is 1.16, so every native client already supports {@code setRotation}
 * (introduced for 1.13+ protocols); only ViaVersion can put an older client behind a newer server.
 */
@Setter
public class RecoilCompatibility {

	private Supplier<ViaAPI<?>> viaApiSupplier;

	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		ViaAPI<?> viaAPI = viaApiSupplier == null ? null : viaApiSupplier.get();

		boolean rotate = viaAPI == null ||
		                 viaAPI.getPlayerVersion(player.getUniqueId()) >= ProtocolVersion.v1_13.getVersion();

		if (rotate)
			// Use Entity#setRotation for future updates that are still not updated to avoid issues
			player.setRotation(player.getLocation().getYaw() + newYaw, player.getLocation().getPitch() + newPitch);
	}

}
