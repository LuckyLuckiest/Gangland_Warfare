package me.luckyraven.compatibility.version.recoil;

import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import net.minecraft.server.v1_13_R2.PacketPlayOutPosition;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Recoil_1_13_R2 extends RecoilCompatibility {

	private final Set<PacketPlayOutPosition.EnumPlayerTeleportFlags> ABSOLUTE_FLAGS = new HashSet<>(
			Arrays.asList(PacketPlayOutPosition.EnumPlayerTeleportFlags.X,
						  PacketPlayOutPosition.EnumPlayerTeleportFlags.Y,
						  PacketPlayOutPosition.EnumPlayerTeleportFlags.Z));
	private final Set<PacketPlayOutPosition.EnumPlayerTeleportFlags> RELATIVE_FLAGS = new HashSet<>(
			Arrays.asList(PacketPlayOutPosition.EnumPlayerTeleportFlags.X,
						  PacketPlayOutPosition.EnumPlayerTeleportFlags.Y,
						  PacketPlayOutPosition.EnumPlayerTeleportFlags.Z,
						  PacketPlayOutPosition.EnumPlayerTeleportFlags.X_ROT,
						  PacketPlayOutPosition.EnumPlayerTeleportFlags.Y_ROT));

	@Override
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		PacketPlayOutPosition packet = new PacketPlayOutPosition(0D, 0D, 0D, newYaw, newPitch,
																 position ? RELATIVE_FLAGS : ABSOLUTE_FLAGS, 0);

		(((CraftPlayer) player).getHandle()).playerConnection.sendPacket(packet);
	}
}
