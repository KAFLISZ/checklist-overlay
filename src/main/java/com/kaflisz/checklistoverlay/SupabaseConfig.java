package com.kaflisz.checklistoverlay;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Supabase project URL + anon (public) API key. Defaults live right here in
 * source, since this mod is built for one specific group's own project and
 * the anon key is meant to be public by design -- the website's front-end
 * JS ships the exact same key. Real access control is enforced by Row
 * Level Security policies on the Supabase project itself, not by keeping
 * this key secret.
 *
 * A properties file in the config folder can still override these if you
 * ever want to point at a different project without recompiling -- handy
 * for testing against a second/staging project -- but it's optional; the
 * hardcoded defaults below are used whenever the file is absent or blank.
 */
public class SupabaseConfig {
    // TODO: fill these in with your project's real values before building --
    // Supabase Dashboard > Project Settings > Data API.
    private static final String DEFAULT_URL = "https://hwrlthmnpnmjeloymyor.supabase.co";
    private static final String DEFAULT_ANON_KEY = "sb_publishable_cluBMZeurL49fLTABxG-8w_J0Aj7SHA";

    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("checklist-overlay-supabase.properties");

    public String url = DEFAULT_URL;
    public String anonKey = DEFAULT_ANON_KEY;

    public boolean isConfigured() {
        return !url.isBlank() && !anonKey.isBlank()
            && !url.contains("YOUR_PROJECT_REF") && !anonKey.equals("YOUR_ANON_PUBLIC_KEY");
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            writeTemplate();
            return; // no override file -- stick with the hardcoded defaults above
        }

        try (var in = Files.newInputStream(CONFIG_PATH)) {
            Properties props = new Properties();
            props.load(in);
            String overrideUrl = props.getProperty("supabase_url", "").trim();
            String overrideKey = props.getProperty("supabase_anon_key", "").trim();

            // Blank lines in the file just mean "no override" -- keep the
            // hardcoded default rather than replacing it with an empty string.
            if (!overrideUrl.isBlank()) {
                url = overrideUrl.endsWith("/") ? overrideUrl.substring(0, overrideUrl.length() - 1) : overrideUrl;
            }
            if (!overrideKey.isBlank()) {
                anonKey = overrideKey;
            }
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to read Supabase config override: " + e.getMessage());
        }
    }

    private void writeTemplate() {
        String template = """
            # Checklist Overlay -- Supabase connection OVERRIDE (optional)
            #
            # The mod already has a Supabase project baked in at build time.
            # Leave both lines below blank to just use that. Only fill these in
            # if you want THIS install to point at a different project (e.g.
            # a personal test project) without recompiling the mod.
            #
            # Find these in your Supabase project: Project Settings > Data API.

            supabase_url=
            supabase_anon_key=
            """;
        try {
            Files.writeString(CONFIG_PATH, template, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[ChecklistOverlay] Failed to write Supabase config template: " + e.getMessage());
        }
    }
}
