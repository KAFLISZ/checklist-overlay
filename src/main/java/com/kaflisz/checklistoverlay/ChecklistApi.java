package com.kaflisz.checklistoverlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for Supabase's auto-generated REST API (PostgREST). Every
 * method here does a blocking HTTP call and is meant to be invoked from a
 * background thread (see ChecklistOverlayMod's sync executor) -- never
 * call these directly from the render/tick thread.
 */
public class ChecklistApi {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    public record RemoteItem(
        String id, String itemId, int targetQty, int contributedQty, boolean obtained, int position
    ) {}

    public record ChecklistMeta(String id, String updatedAt) {}

    private final SupabaseConfig config;

    public ChecklistApi(SupabaseConfig config) {
        this.config = config;
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(config.url + "/rest/v1/" + path))
            .timeout(Duration.ofSeconds(10))
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer " + config.anonKey)
            .header("Content-Type", "application/json");
    }

    /** Looks up a checklist by its short share code. Returns null if not found or on error. */
    public ChecklistMeta fetchChecklistMeta(String code) {
        try {
            String encoded = URLEncoder.encode(code, StandardCharsets.UTF_8);
            HttpRequest req = baseRequest("checklists?code=eq." + encoded + "&select=id,updated_at")
                .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[ChecklistOverlay] fetchChecklistMeta got HTTP " + resp.statusCode()
                    + ": " + resp.body());
                return null;
            }

            JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
            if (arr.isEmpty()) return null;
            JsonObject obj = arr.get(0).getAsJsonObject();
            return new ChecklistMeta(obj.get("id").getAsString(), obj.get("updated_at").getAsString());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("[ChecklistOverlay] fetchChecklistMeta failed: " + e.getMessage());
            return null;
        }
    }

    /** Fetches every item row for a checklist, ordered by position. Returns an empty list on error. */
    public List<RemoteItem> fetchItems(String checklistId) {
        List<RemoteItem> result = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(checklistId, StandardCharsets.UTF_8);
            HttpRequest req = baseRequest(
                "checklist_items?checklist_id=eq." + encoded
                    + "&select=id,item_id,target_qty,contributed_qty,obtained,position&order=position.asc"
            ).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[ChecklistOverlay] fetchItems got HTTP " + resp.statusCode()
                    + ": " + resp.body());
                return result;
            }

            JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                result.add(new RemoteItem(
                    obj.get("id").getAsString(),
                    obj.get("item_id").getAsString(),
                    obj.get("target_qty").getAsInt(),
                    obj.get("contributed_qty").getAsInt(),
                    obj.get("obtained").getAsBoolean(),
                    obj.get("position").getAsInt()
                ));
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("[ChecklistOverlay] fetchItems failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Reports how much of an item this player currently holds. Upserts on
     * the (item_id, contributor) unique constraint -- a repeat call from
     * the same player just overwrites their previous number rather than
     * adding to it, since this reports a live snapshot, not a delta.
     */
    public void upsertContribution(String itemRowId, String contributor, int quantity) {
        try {
            String body = "{"
                + "\"item_id\":\"" + escape(itemRowId) + "\","
                + "\"contributor\":\"" + escape(contributor) + "\","
                + "\"quantity\":" + quantity
                + "}";

            HttpRequest req = baseRequest("checklist_contributions?on_conflict=item_id,contributor")
                .header("Prefer", "resolution=merge-duplicates")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[ChecklistOverlay] upsertContribution got HTTP " + resp.statusCode()
                    + ": " + resp.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("[ChecklistOverlay] upsertContribution failed: " + e.getMessage());
        }
    }

    /** Removes an item from the shared list entirely (affects everyone syncing to it). */
    public void deleteItem(String itemRowId) {
        try {
            String encoded = URLEncoder.encode(itemRowId, StandardCharsets.UTF_8);
            HttpRequest req = baseRequest("checklist_items?id=eq." + encoded)
                .DELETE().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[ChecklistOverlay] deleteItem got HTTP " + resp.statusCode()
                    + ": " + resp.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            System.err.println("[ChecklistOverlay] deleteItem failed: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
