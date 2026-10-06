package fr.maxlego08.text.font;

import fr.maxlego08.text.api.TextGeneratorPlugin;
import fr.maxlego08.text.api.TextManager;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads every {@code .ttf} file of the {@code plugins/zTextGenerator/fonts} folder, generates the
 * resource pack which contains the glyphs and sends that pack to the players.
 *
 * <p>To add a font, drop a {@code .ttf} file inside the {@code fonts} folder and write the name of
 * the file (without the extension) as alphabet inside your texts, for example
 * {@code alphabet: "MyFont"}.</p>
 */
public class TtfFontManager {

    private static final String FONTS_README = """
            Put your .ttf (or .otf) files inside this folder.

            The plugin uses the name of the file as alphabet name, for example a file called
            MyFont.ttf can be used with: alphabet: "MyFont".

            Nothing else is required, the plugin generates the resource pack and, if
            ttf.resource-pack.auto-host is enabled, sends it to your players.
            """;

    private final TextGeneratorPlugin plugin;
    private final Map<String, TtfFont> fonts = new LinkedHashMap<>();

    private boolean enabled = true;
    private boolean active = false;
    private boolean sendOnJoin = true;
    private boolean loggedWarning = false;
    private int size = 9;
    private int packFormat = 46;
    private TtfFont defaultFont;
    private File packFile;
    private byte[] packHash;
    private TtfResourcePackServer server;
    private String packUrl = "";
    private String publicAddress = "";

    public TtfFontManager(TextGeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads every font of the {@code fonts} folder.
     */
    public void load() {

        System.setProperty("java.awt.headless", "true");

        this.fonts.clear();
        this.defaultFont = null;

        this.enabled = this.plugin.getConfig().getBoolean("ttf.enabled", true);
        this.size = Math.max(4, Math.min(64, this.plugin.getConfig().getInt("ttf.size", 9)));
        this.packFormat = this.plugin.getConfig().getInt("ttf.pack-format", 46);

        File folder = new File(this.plugin.getDataFolder(), "fonts");
        if (!folder.exists() && !folder.mkdirs()) {
            this.plugin.getLogger().warning("Unable to create the folder " + folder.getAbsolutePath());
        }

        File readme = new File(folder, "README.txt");
        if (!readme.exists()) {
            try {
                Files.writeString(readme.toPath(), FONTS_README, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                this.plugin.debug("Unable to write the fonts README : " + exception.getMessage());
            }
        }

        if (!this.enabled) {
            return;
        }

        for (File file : TtfFontLoader.findFontFiles(folder)) {
            TtfFontLoader.load(file, this.size, this.plugin.getLogger()).ifPresent(font -> this.fonts.put(font.getName().toLowerCase(Locale.ROOT), font));
        }

        Optional<TtfFont> optional = this.resolveFont(this.plugin.getConfig().getString("ttf.default-font", ""));
        optional.ifPresent(font -> this.defaultFont = font);

        if (this.defaultFont == null && this.fonts.size() == 1) {
            this.defaultFont = this.fonts.values().iterator().next();
        }

        if (!this.fonts.isEmpty()) {
            List<String> names = new ArrayList<>();
            this.fonts.values().forEach(font -> names.add(font.getName()));
            this.plugin.getLogger().info("Loaded " + this.fonts.size() + " TTF font(s) : " + String.join(", ", names));
        }
    }

    /**
     * Checks if the TTF system has to be used instead of the font of a pack plugin.
     *
     * @return true if the TTF system is enabled and at least one font is loaded
     */
    public boolean hasFonts() {
        return this.enabled && !this.fonts.isEmpty();
    }

    /**
     * Generates the resource pack and starts the http server if it is enabled.
     */
    public void activate() {

        if (!hasFonts()) {
            this.active = false;
            return;
        }

        this.active = true;

        this.packUrl = this.plugin.getConfig().getString("ttf.resource-pack.url", "");
        this.publicAddress = this.plugin.getConfig().getString("ttf.resource-pack.public-address", "");
        this.sendOnJoin = this.plugin.getConfig().getBoolean("ttf.resource-pack.send-on-join", true);

        this.generatePack();
        this.startServer();
    }

    private void generatePack() {

        File folder = new File(this.plugin.getDataFolder(), "generated");
        if (!folder.exists() && !folder.mkdirs()) {
            this.plugin.getLogger().warning("Unable to create the folder " + folder.getAbsolutePath());
        }

        TtfPackGenerator.GeneratedPack pack = TtfPackGenerator.generate(
                new ArrayList<>(this.fonts.values()),
                folder,
                this.packFormat,
                "zTextGenerator TTF fonts",
                this.plugin.getLogger());

        if (pack == null) {
            this.packFile = null;
            this.packHash = null;
            return;
        }

        this.packFile = pack.file();
        this.packHash = pack.hash();
        this.plugin.getLogger().info("Generated the resource pack : " + pack.file().getAbsolutePath());
    }

    private void startServer() {

        if (this.server != null) {
            this.server.stop();
            this.server = null;
        }

        if (this.packFile == null || !this.plugin.getConfig().getBoolean("ttf.resource-pack.auto-host", false)) {
            return;
        }

        String host = this.plugin.getConfig().getString("ttf.resource-pack.host", "0.0.0.0");
        int port = this.plugin.getConfig().getInt("ttf.resource-pack.port", 8163);

        try {
            this.server = new TtfResourcePackServer(host, port, this.packFile, this.plugin.getLogger());
            this.plugin.getLogger().info("Resource pack server started on port " + port);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Unable to start the resource pack server : " + exception.getMessage());
        }
    }

    /**
     * Gets the url used to send the pack to the players.
     *
     * @return the url of the pack, or an empty string if the pack cannot be reached
     */
    public String getPackUrl() {

        if (this.packUrl != null && !this.packUrl.isEmpty()) {
            return this.packUrl;
        }

        if (this.server != null) {
            String address = this.publicAddress == null || this.publicAddress.isEmpty() ? this.plugin.getConfig().getString("ttf.resource-pack.host", "") : this.publicAddress;
            if (address != null && !address.isEmpty() && !address.equals("0.0.0.0")) {
                return this.server.getUrl(address);
            }
        }

        return "";
    }

    /**
     * Sends the generated resource pack to a player.
     *
     * @param player the player
     */
    public void sendPack(Player player) {

        if (!this.active || this.packFile == null) {
            return;
        }

        String url = getPackUrl();
        if (url == null || url.isEmpty()) {
            if (!this.loggedWarning) {
                this.loggedWarning = true;
                this.plugin.getLogger().warning("The TTF resource pack is not sent to the players : enable ttf.resource-pack.auto-host or fill ttf.resource-pack.url.");
            }
            return;
        }

        try {
            player.setResourcePack(url, this.packHash);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Unable to send the resource pack to " + player.getName() + " : " + exception.getMessage());
        }
    }

    /**
     * Gets a font from its name (the name of the file without the extension).
     *
     * @param name the name of the font
     * @return the font, or an empty optional if this font does not exist
     */
    public Optional<TtfFont> resolveFont(String name) {

        if (name == null || name.isBlank()) {
            return this.defaultFont == null ? this.fonts.values().stream().findFirst() : Optional.of(this.defaultFont);
        }

        TtfFont font = this.fonts.get(name.toLowerCase(Locale.ROOT));
        if (font != null) {
            return Optional.of(font);
        }

        return this.fonts.values().stream().filter(loaded -> loaded.getName().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * Registers one alphabet per font, so {@code alphabet: "<font name>"} can be used directly
     * inside the texts.
     *
     * @param textManager the text manager
     */
    public void registerAlphabets(TextManager textManager) {

        if (!this.active) {
            return;
        }

        this.fonts.values().forEach(font -> {
            if (textManager.getAlphabet(font.getName()).isPresent()) {
                return;
            }
            textManager.registerAlphabet(new TtfAlphabet(this.plugin, font.getName(), font.getFile(), font));
            this.plugin.getLogger().info("Registered the TTF alphabet " + font.getName());
        });
    }

    /**
     * Reloads the fonts and regenerates the pack.
     */
    public void reload() {
        this.load();
        if (this.active) {
            this.activate();
        }
    }

    public void shutdown() {
        if (this.server != null) {
            this.server.stop();
            this.server = null;
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean isSendOnJoin() {
        return this.sendOnJoin;
    }

    public Optional<TtfFont> getDefaultFont() {
        return Optional.ofNullable(this.defaultFont);
    }

    public List<TtfFont> getFonts() {
        return new ArrayList<>(this.fonts.values());
    }

    public File getPackFile() {
        return this.packFile;
    }
}
