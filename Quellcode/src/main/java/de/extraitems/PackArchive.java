package de.extraitems;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

final class PackArchive {
    static final long MAX_BYTES = 64L * 1024 * 1024;
    private PackArchive() {}
    static byte[] build(Path folder) throws IOException {
        if (!Files.isRegularFile(folder.resolve("pack.mcmeta"))) throw new IOException("pack.mcmeta fehlt");
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out); var walk = Files.walk(folder)) {
            long total = 0;
            for (Path file : walk.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Symlinks im Pack sind nicht erlaubt: " + file.getFileName());
                if (!Files.isRegularFile(file)) continue;
                total += Files.size(file);
                if (total > MAX_BYTES) throw new IOException("Pack über 64 MiB");
                String name = folder.relativize(file).toString().replace(File.separatorChar, '/');
                var entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry);
                Files.copy(file, zip); zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    static byte[] sha1(byte[] data) {
        try { return MessageDigest.getInstance("SHA-1").digest(data); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String hex(byte[] data) { return HexFormat.of().formatHex(data); }
    static void writeAtomic(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), ".pack-", ".tmp");
        try {
            Files.write(temp, data);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
