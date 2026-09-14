package de.extraitems;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class PackService implements AutoCloseable {
    private final ExtraItemsPlugin plugin;
    private PackHttpServer http;
    private String url;
    private byte[] hash;
    private String error = "Noch nicht initialisiert";
    PackService(ExtraItemsPlugin plugin) { this.plugin = plugin; }
    void start() {
        try {
            Path root = plugin.getDataFolder().toPath();
            try (InputStream stream = plugin.getResource("pack-files.txt")) {
                if (stream == null) throw new IOException("Pack-Manifest fehlt im JAR");
                for (String relative : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                    if (relative.isBlank()) continue;
                    Path target = root.resolve("resourcepack").resolve(relative).normalize();
                    if (!target.startsWith(root.resolve("resourcepack"))) throw new IOException("Ungültiger Manifestpfad");
                    if (!Files.exists(target)) plugin.saveResource("resourcepack/" + relative, false);
                }
            }
            byte[] bytes = PackArchive.build(root.resolve("resourcepack"));
            hash = PackArchive.sha1(bytes);
            PackArchive.writeAtomic(root.resolve("generated/extraitems.zip"), bytes);
            plugin.getLogger().info("Ressourcenpaket: generated/extraitems.zip | SHA-1 " + sha1());
            if (plugin.getConfig().getBoolean("resource-pack.http.enabled", true)) {
                int port = ItemRegistry.range(plugin.getConfig().getInt("resource-pack.http.port", 8123), 1, 65535, "HTTP-Port");
                http = new PackHttpServer(plugin.getConfig().getString("resource-pack.http.bind", "0.0.0.0"), port, bytes, sha1());
            }
            url = validateUrl(plugin.getConfig().getString("resource-pack.public-url", ""));
            error = null;
        } catch (Exception e) {
            error = e.getMessage();
            plugin.getLogger().severe("Pflicht-Ressourcenpaket nicht bereit: " + error + ". Spieler bleiben gesperrt.");
        }
    }
    static String validateUrl(String value) {
        URI uri = URI.create(value.trim());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("resource-pack.public-url muss eine vollständige öffentliche HTTP(S)-Download-URL sein");
        return uri.toASCIIString();
    }
    boolean ready() { return error == null; }
    String error() { return error; }
    String url() { return url; }
    byte[] hash() { return hash.clone(); }
    String sha1() { return hash == null ? "—" : PackArchive.hex(hash); }
    @Override public void close() { if (http != null) http.close(); }
}
