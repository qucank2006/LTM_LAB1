package util;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Shared configuration loaded once at startup from an external UTF-8 file. */
public final class AppConfig {
    private static final Path CONFIG_PATH = Path.of(
            System.getProperty("chat.config", "app.properties")).toAbsolutePath();
    private static final Properties PROPERTIES = load();

    private AppConfig() {
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read configuration: " + CONFIG_PATH, e);
        }
    }

    private static String required(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration '" + key + "' in " + CONFIG_PATH);
        }
        return value.trim();
    }

    public static String serverHost() {
        return required("server.host");
    }

    public static int serverPort() {
        try {
            int port = Integer.parseInt(required("server.port"));
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // Report the property name without exposing any credentials.
        }
        throw new IllegalStateException("server.port must be between 1 and 65535 in " + CONFIG_PATH);
    }

    public static String databaseUrl() {
        return required("db.url");
    }

    public static String databaseUsername() {
        return required("db.username");
    }

    public static String databasePassword() {
        String password = PROPERTIES.getProperty("db.password");
        if (password == null) {
            throw new IllegalStateException("Missing configuration 'db.password' in " + CONFIG_PATH);
        }
        return password;
    }
}
