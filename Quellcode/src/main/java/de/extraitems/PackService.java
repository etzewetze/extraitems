package de.extraitems;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class PackService implements AutoCloseable {
    enum Mode { SELF_HOST, EXTERNAL, DISABLED }

    private final ExtraItemsPlugin plugin;
    private PackHttpServer http;
    private Mode mode = Mode.DISABLED;
    private String fixedUrl;
    private String publicHost;
    private String scheme;
    private int advertisedPort;
    private byte[] hash;
    private boolean required;
    private String error = "Noch nicht initialisiert";

    PackService(ExtraItemsPlugin plugin) {
        this.plugin = plugin;
        // Fail closed even when an item/import error happens before the pack can be built.
        required = plugin.getConfig().getBoolean("resource-pack.required", true);
    }

    void start() {
        try {
            Path root = plugin.getDataFolder().toPath();
            java.util.List<String> manifest;
            try (InputStream stream = plugin.getResource("pack-files.txt")) {
                if (stream == null) throw new IOException("Pack-Manifest fehlt im JAR");
                manifest = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            }
            String revision;
            try (InputStream stream = plugin.getResource("pack-revision.txt")) {
                if (stream == null) throw new IOException("Pack-Revision fehlt im JAR");
                revision = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            PackDefaults.Result defaults = PackDefaults.sync(root, manifest, revision, relative -> {
                try (InputStream stream = plugin.getResource("resourcepack/" + relative)) {
                    if (stream == null) throw new IOException("Pack-Ressource fehlt im JAR: " + relative);
                    return stream.readAllBytes();
                }
            });
            if (defaults.updated() > 0) {
                plugin.getLogger().info(defaults.updated() + " Standard-Packdatei(en) auf Revision "
                        + revision + " aktualisiert; Sicherung: " + defaults.backup());
            }

            byte[] bytes = PackArchive.build(root.resolve("resourcepack"),
                    root.resolve(CraftEngineBundleImporter.OVERLAY));
            hash = PackArchive.sha1(bytes);
            PackArchive.writeAtomic(root.resolve("generated/extraitems.zip"), bytes);
            plugin.getLogger().info("Ressourcenpaket: generated/extraitems.zip | SHA-1 " + sha1());

            FileConfiguration config = plugin.getConfig();
            required = config.getBoolean("resource-pack.required", true);
            mode = resolveMode(config);
            switch (mode) {
                case SELF_HOST -> startSelfHost(config, bytes);
                case EXTERNAL -> {
                    String configured = config.getString("resource-pack.external-url",
                            config.getString("resource-pack.public-url", ""));
                    fixedUrl = validateUrl(configured);
                }
                case DISABLED -> {
                    if (required) throw new IllegalArgumentException(
                            "resource-pack.mode ist disabled, aber resource-pack.required ist true");
                }
            }
            error = null;
            plugin.getLogger().info("Pack-Auslieferung: " + modeName()
                    + (required ? " (Pflicht)" : " (optional)"));
        } catch (Exception exception) {
            error = exception.getMessage();
            plugin.getLogger().severe("Ressourcenpaket nicht bereit: " + error
                    + ". Normale Spieler bleiben bei Pflichtmodus gesperrt.");
        }
    }

    private Mode resolveMode(FileConfiguration config) {
        String configured = config.getString("resource-pack.mode");
        if (configured == null || configured.isBlank()) {
            String legacyUrl = config.getString("resource-pack.public-url", "");
            if (!legacyUrl.isBlank()) {
                if (config.getBoolean("resource-pack.http.enabled", false)) return Mode.SELF_HOST;
                return Mode.EXTERNAL;
            }
            return config.getBoolean("resource-pack.http.enabled", true) ? Mode.SELF_HOST : Mode.DISABLED;
        }
        try {
            return Mode.valueOf(configured.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("resource-pack.mode muss self-host, external oder disabled sein");
        }
    }

    private void startSelfHost(FileConfiguration config, byte[] bytes) throws IOException {
        int port = ItemRegistry.range(config.getInt("resource-pack.self-host.port",
                config.getInt("resource-pack.http.port", 8123)), 1, 65535, "HTTP-Port");
        String bind = config.getString("resource-pack.self-host.bind",
                config.getString("resource-pack.http.bind", "0.0.0.0"));
        http = new PackHttpServer(bind, port, bytes, sha1());

        scheme = config.getString("resource-pack.self-host.scheme", "http").toLowerCase(java.util.Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("self-host.scheme muss http oder https sein");
        }
        publicHost = config.getString("resource-pack.self-host.public-host", "auto").trim();
        advertisedPort = ItemRegistry.range(
                config.getInt("resource-pack.self-host.advertised-port", port),
                1, 65535, "Öffentlicher HTTP-Port");

        String legacyUrl = config.getString("resource-pack.public-url", "");
        if (!legacyUrl.isBlank() && !config.contains("resource-pack.mode")) fixedUrl = validateUrl(legacyUrl);
    }

    String url(String joiningAddress) {
        if (fixedUrl != null) return fixedUrl;
        if (mode != Mode.SELF_HOST || http == null) throw new IllegalStateException("Keine Pack-Downloadadresse aktiv");

        String host = publicHost;
        if (host.equalsIgnoreCase("auto")) host = joiningHost(joiningAddress);
        return buildSelfHostedUrl(scheme, host, advertisedPort, sha1());
    }

    static String joiningHost(String joiningAddress) {
        if (joiningAddress == null || joiningAddress.isBlank()) {
            throw new IllegalStateException("Serveradresse konnte nicht automatisch erkannt werden; self-host.public-host setzen");
        }
        try {
            URI parsed = new URI("tcp://" + joiningAddress.trim());
            String host = parsed.getHost();
            if (host == null || host.isBlank() || host.equals("0.0.0.0") || host.equals("::")) {
                throw new URISyntaxException(joiningAddress, "Host fehlt");
            }
            if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
            return host;
        } catch (URISyntaxException error) {
            throw new IllegalStateException("Serveradresse konnte nicht automatisch erkannt werden; self-host.public-host setzen", error);
        }
    }

    static String buildSelfHostedUrl(String scheme, String host, int port, String sha1) {
        try {
            int uriPort = (scheme.equalsIgnoreCase("http") && port == 80)
                    || (scheme.equalsIgnoreCase("https") && port == 443) ? -1 : port;
            URI uri = new URI(scheme, null, host, uriPort, "/extraitems.zip", "sha1=" + sha1, null);
            if (uri.getHost() == null || uri.getUserInfo() != null) throw new URISyntaxException(host, "Ungültiger Host");
            return uri.toASCIIString();
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Ungültiger öffentlicher Host: " + host, error);
        }
    }

    static String validateUrl(String value) {
        URI uri = URI.create(value == null ? "" : value.trim());
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Die externe Pack-URL muss eine vollständige HTTP(S)-Download-URL sein");
        }
        return uri.toASCIIString();
    }

    boolean ready() { return error == null; }
    boolean required() { return required; }
    boolean deliveryEnabled() { return mode != Mode.DISABLED; }
    String error() { return error; }
    String modeName() { return mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }
    byte[] hash() { return hash.clone(); }
    String sha1() { return hash == null ? "—" : PackArchive.hex(hash); }

    @Override public void close() {
        if (http != null) http.close();
    }
}
