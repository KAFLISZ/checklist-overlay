package com.kaflisz.checklistoverlay;

import net.minecraft.util.Identifier;

/**
 * Packet channel identifiers shared between client and server code. This
 * targets 1.20.1's PacketByteBuf-based networking API (Fabric API's
 * ClientPlayNetworking / ServerPlayNetworking) -- the newer typed
 * CustomPayload record API only exists from 1.20.5 onward.
 *
 * C2S = client asks the server to do something.
 * S2C = server pushes state to a client.
 */
public class NetworkChannels {
    // C2S
    public static final Identifier REQUEST_LIST_NAMES = Identifier.of("checklistoverlay", "request_list_names");
    public static final Identifier JOIN_LIST = Identifier.of("checklistoverlay", "join_list");
    public static final Identifier LEAVE_LIST = Identifier.of("checklistoverlay", "leave_list");
    public static final Identifier CREATE_LIST = Identifier.of("checklistoverlay", "create_list");
    public static final Identifier ADD_ITEM = Identifier.of("checklistoverlay", "add_item");
    public static final Identifier REMOVE_ITEM = Identifier.of("checklistoverlay", "remove_item");

    // S2C
    public static final Identifier LIST_NAMES = Identifier.of("checklistoverlay", "list_names");
    public static final Identifier LIST_STATE = Identifier.of("checklistoverlay", "list_state");
    public static final Identifier ERROR_MESSAGE = Identifier.of("checklistoverlay", "error_message");
}
