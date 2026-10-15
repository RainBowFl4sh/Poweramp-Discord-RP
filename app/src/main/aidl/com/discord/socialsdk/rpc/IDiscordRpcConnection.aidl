// See IDiscordRpcService.aidl (transaction 1 = sendFrame, 2 = disconnect).
package com.discord.socialsdk.rpc;

interface IDiscordRpcConnection {
    /** Sends one JSON frame (same JSON as the desktop RPC: {"cmd":"SET_ACTIVITY",...}). */
    void sendFrame(String frame);
    void disconnect();
}
