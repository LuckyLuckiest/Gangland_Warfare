package me.luckyraven.compatibility.version;

import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;
import net.minecraft.server.v1_16_R3.PacketPlayOutPosition;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static net.minecraft.server.v1_16_R3.PacketPlayOutPosition.EnumPlayerTeleportFlags;

public class v1_16_R3 extends RecoilCompatibility {

	private final Set<EnumPlayerTeleportFlags> ABSOLUTE_FLAGS = new HashSet<>(
			Arrays.asList(EnumPlayerTeleportFlags.X, EnumPlayerTeleportFlags.Y, EnumPlayerTeleportFlags.Z));
	private final Set<EnumPlayerTeleportFlags> RELATIVE_FLAGS = new HashSet<>(
			Arrays.asList(EnumPlayerTeleportFlags.X, EnumPlayerTeleportFlags.Y, EnumPlayerTeleportFlags.Z,
						  EnumPlayerTeleportFlags.X_ROT, EnumPlayerTeleportFlags.Y_ROT));

	@Override
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		PacketPlayOutPosition packet = new PacketPlayOutPosition(0D, 0D, 0D, newYaw, newPitch,
																 position ? RELATIVE_FLAGS : ABSOLUTE_FLAGS, 0);

		(((CraftPlayer) player).getHandle()).playerConnection.sendPacket(packet);
	}

}
