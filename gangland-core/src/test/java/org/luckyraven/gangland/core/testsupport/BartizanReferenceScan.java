package org.luckyraven.gangland.core.testsupport;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * WS7 G5 fix round 1 (review C1/C2 slipped past {@link BartizanBlindScan}; ruling W25 "allowlist test"): a raw JVM
 * class-file constant-pool scanner that finds every compiled class under a directory whose constant pool contains a
 * UTF8 entry naming a {@code org/luckyraven/bartizan/*} type — catching a Bartizan reference ANYWHERE in a class
 * (fields, local variables, method bodies, imports), not just in a scanned annotation's own method/constructor
 * signature the way {@link BartizanBlindScan} does. The two tools are complementary: {@code BartizanBlindScan}
 * proves a Bartizan-typed SIGNATURE never crashes Keystone's reflective bean/listener scan; this one proves the
 * complete set of classes referencing Bartizan AT ALL matches an explicitly audited allowlist, so a stray body
 * reference (like {@code BartizanNpcWeapons}'s unguarded {@code BartizanApi.class} literal, C2) is caught by a red
 * test instead of silently shipping.
 *
 * <p>No ASM/bytecode-parsing dependency exists anywhere in this reactor (confirmed by a repo-wide grep before
 * writing this) — a minimal constant-pool-only parser is a few dozen lines and needs no new dependency (ponytail).
 * Follows the JVM class file format (JVM Spec &sect;4.4): walks the constant pool tag-by-tag, decoding only what is
 * needed to (a) know each entry's byte length so the cursor advances correctly and (b) collect every UTF8 entry's
 * text — {@link DataInputStream#readUTF()} reads exactly the {@code CONSTANT_Utf8_info} wire format (u2 length +
 * that many bytes of modified UTF-8), so no bespoke string decoding is needed either. This is a coarse,
 * over-inclusive presence check: it does not cross-reference a UTF8 entry back to a specific {@code Class}/
 * {@code Fieldref}/{@code Methodref} entry to prove exactly how it's used — a UTF8 constant containing
 * {@code "org/luckyraven/bartizan/"} appears whenever the class references a Bartizan type as an import, a field, a
 * local variable, a method-body call or a descriptor, which is exactly the "anywhere in the class" coverage this
 * test adds on top of {@code BartizanBlindScan}'s signature-only view.
 */
public final class BartizanReferenceScan {

	private static final String NEEDLE = "org/luckyraven/bartizan/";

	private BartizanReferenceScan() {
	}

	/**
	 * Walks every {@code .class} file under {@code classesRoot} (a module's {@code target/classes}) and returns the
	 * fully-qualified (dotted) names of classes whose constant pool contains at least one UTF8 entry referencing a
	 * Bartizan type. Returns an empty set (rather than throwing) if {@code classesRoot} does not exist, so a caller
	 * that forgot to build first gets an honest "found nothing" rather than a confusing IO failure — callers should
	 * assert against a known non-empty allowlist, which itself catches a not-actually-built classesRoot.
	 */
	public static Set<String> findBartizanReferencingClasses(Path classesRoot) throws IOException {
		Set<String> found = new LinkedHashSet<>();
		if (!Files.isDirectory(classesRoot)) return found;

		List<Path> classFiles;
		try (var walk = Files.walk(classesRoot)) {
			classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
		}
		for (Path path : classFiles) {
			if (referencesBartizan(path)) {
				found.add(toClassName(classesRoot, path));
			}
		}
		return found;
	}

	private static boolean referencesBartizan(Path classFile) throws IOException {
		try (InputStream in = Files.newInputStream(classFile)) {
			return scanConstantPool(in);
		}
	}

	/**
	 * Reads the constant pool of one class file, returning whether any UTF8 entry contains {@link #NEEDLE}. Entries
	 * are 1-indexed and {@code constant_pool_count} is (actual entry count + 1); a {@code Long}/{@code Double}
	 * entry occupies TWO pool indices even though it is one entry (a well-known JVM spec quirk) — the extra {@code
	 * i++} below accounts for that.
	 */
	private static boolean scanConstantPool(InputStream rawIn) throws IOException {
		DataInputStream in = new DataInputStream(rawIn);
		int magic = in.readInt();
		if (magic != 0xCAFEBABE) return false; // not a class file - skip rather than throw

		in.readUnsignedShort(); // minor_version
		in.readUnsignedShort(); // major_version
		int constantPoolCount = in.readUnsignedShort();

		boolean hit = false;
		for (int i = 1; i < constantPoolCount; i++) {
			int tag = in.readUnsignedByte();
			switch (tag) {
				case 1 -> { // CONSTANT_Utf8
					if (in.readUTF().contains(NEEDLE)) hit = true;
				}
				case 7, 8, 16, 19, 20 -> in.skipBytes(2);       // Class, String, MethodType, Module, Package
				case 15 -> in.skipBytes(3);                     // MethodHandle
				case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4); // Integer, Float, *ref, NameAndType, *Dynamic
				case 5, 6 -> {                                   // Long, Double: 8 bytes AND two pool slots
					in.skipBytes(8);
					i++;
				}
				default -> throw new IOException(
						"Unrecognised constant pool tag " + tag + " at index " + i + " - javac emitted something " +
						"this scanner doesn't know how to skip; extend the switch above rather than ignore it.");
			}
		}
		return hit;
	}

	private static String toClassName(Path root, Path classFile) {
		String relative = root.relativize(classFile).toString().replace('\\', '/');
		String withoutExt = relative.substring(0, relative.length() - ".class".length());
		return withoutExt.replace('/', '.');
	}
}
