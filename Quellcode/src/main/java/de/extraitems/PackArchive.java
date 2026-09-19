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
        return build(folder, new Path[0]);
    }
    static byte[] build(Path folder, Path... overlays) throws IOException {
        if (!Files.isRegularFile(folder.resolve("pack.mcmeta"))) throw new IOException("pack.mcmeta fehlt");
        SortedMap<String, byte[]> files = new TreeMap<>();
        collect(folder, files);
        for (Path overlay : overlays) {
            if (overlay != null && Files.exists(overlay, LinkOption.NOFOLLOW_LINKS)) collect(overlay, files);
        }
        long total = files.values().stream().mapToLong(bytes -> bytes.length).sum();
        if (total > MAX_BYTES) throw new IOException("Pack über 64 MiB");
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                String name = file.getKey();
                var entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry);
                zip.write(file.getValue()); zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    private static void collect(Path root, Map<String, byte[]> files) throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("Symlinks im Pack sind nicht erlaubt: " + root.getFileName());
        try (var walk = Files.walk(root)) {
            for (Path file : walk.sorted().toList()) {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Symlinks im Pack sind nicht erlaubt: " + file.getFileName());
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = root.relativize(file).toString().replace(File.separatorChar, '/');
                long size = Files.size(file);
                if (size > MAX_BYTES) throw new IOException("Packdatei über 64 MiB: " + name);
                if (!files.containsKey(name)) {
                    long used = files.values().stream().mapToLong(bytes -> bytes.length).sum();
                    if (used + size > MAX_BYTES) throw new IOException("Pack über 64 MiB");
                }
                byte[] bytes = Files.readAllBytes(file);
                byte[] previous = files.putIfAbsent(name, bytes);
                if (previous != null && !Arrays.equals(previous, bytes)) {
                    throw new IOException("Ressourcenpaket-Konflikt: " + name);
                }
            }
        }
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
