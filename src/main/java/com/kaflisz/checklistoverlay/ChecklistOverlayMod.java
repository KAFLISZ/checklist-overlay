package com.kaflisz.checklistoverlay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Common ("main") entrypoint -- runs on both the dedicated server and the
 * client's internal singleplayer server, per Fabric's usual convention.
 * This is where the actual checklist data lives now: every list, its
 * items, and live group progress are all server-authoritative. Clients
 * only ever see what the server chooses to push them.
 */
public class ChecklistOverlayMod implements ModInitializer {
    public static final ServerChecklistManager MANAGER = new ServerChecklistManager();

    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(MANAGER::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MANAGER.onServerStopping());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            MANAGER.onPlayerDisconnect(handler.getPlayer().getUuid()));

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        registerReceivers();
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < 100) return; // ~5 seconds at 20 tps
        tickCounter = 0;

        List<String> activeLists = MANAGER.recomputeAndGetActiveLists(server);
        for (String listName : activeLists) {
            broadcastListState(server, listName);
        }
    }

    private void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.REQUEST_LIST_NAMES, (server, player, handler, buf, sender) -> {
            server.execute(() -> sendListNames(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.JOIN_LIST, (server, player, handler, buf, sender) -> {
            String listName = buf.readString(64);
            server.execute(() -> {
                if (MANAGER.getList(listName) == null) {
                    sendError(player, "That list no longer exists.");
                    return;
                }
                MANAGER.joinList(player.getUuid(), listName);
                sendListState(player, listName);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.LEAVE_LIST, (server, player, handler, buf, sender) -> {
            server.execute(() -> MANAGER.leaveList(player.getUuid()));
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.CREATE_LIST, (server, player, handler, buf, sender) -> {
            String name = buf.readString(64);
            server.execute(() -> {
                if (MANAGER.createList(name)) {
                    MANAGER.joinList(player.getUuid(), name);
                    sendListState(player, name);
                    broadcastListNamesToAll(server);
                } else {
                    sendError(player, "Couldn't create that list (blank, too long, or name already taken).");
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.ADD_ITEM, (server, player, handler, buf, sender) -> {
            String listName = buf.readString(64);
            String itemId = buf.readString(256);
            int targetQty = buf.readVarInt();
            server.execute(() -> {
                if (MANAGER.addItem(listName, itemId, targetQty)) {
                    broadcastListStateToMembers(server, listName);
                } else {
                    sendError(player, "Couldn't add that item -- list may no longer exist.");
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkChannels.REMOVE_ITEM, (server, player, handler, buf, sender) -> {
            String listName = buf.readString(64);
            java.util.UUID itemId = buf.readUuid();
            server.execute(() -> {
                if (MANAGER.removeItem(listName, itemId)) {
                    broadcastListStateToMembers(server, listName);
                }
            });
        });
    }

    // ---------------------------------------------------------------
    // Outgoing packet helpers
    // ---------------------------------------------------------------

    private void sendListNames(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        List<String> names = MANAGER.getListNames();
        buf.writeVarInt(names.size());
        for (String name : names) buf.writeString(name);
        ServerPlayNetworking.send(player, NetworkChannels.LIST_NAMES, buf);
    }

    private void broadcastListNamesToAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            sendListNames(p);
        }
    }

    private void sendListState(ServerPlayerEntity player, String listName) {
        ServerChecklist list = MANAGER.getList(listName);
        if (list == null) return;
        ServerPlayNetworking.send(player, NetworkChannels.LIST_STATE, encodeListState(list));
    }

    private void broadcastListState(MinecraftServer server, String listName) {
        broadcastListStateToMembers(server, listName);
    }

    private void broadcastListStateToMembers(MinecraftServer server, String listName) {
        ServerChecklist list = MANAGER.getList(listName);
        if (list == null) return;
        for (ServerPlayerEntity p : MANAGER.playersJoinedTo(server, listName)) {
            ServerPlayNetworking.send(p, NetworkChannels.LIST_STATE, encodeListState(list));
        }
    }

    private static PacketByteBuf encodeListState(ServerChecklist list) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(list.name);
        buf.writeVarInt(list.items.size());
        for (ServerChecklistItem item : list.items) {
            buf.writeUuid(item.id);
            buf.writeString(item.itemId);
            buf.writeVarInt(item.targetQty);
            buf.writeVarInt(item.contributedQty);
            buf.writeBoolean(item.obtained);
        }
        return buf;
    }

    private static void sendError(ServerPlayerEntity player, String message) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(message);
        ServerPlayNetworking.send(player, NetworkChannels.ERROR_MESSAGE, buf);
    }
}
