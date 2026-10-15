// See IDiscordRpcService.aidl (transaction 1 = onFrame, 2 = onClose).
package com.discord.socialsdk.rpc;

interface IDiscordRpcCallback {
    /** A JSON frame from Discord (DISPATCH/READY, answers to commands, errors). */
    void onFrame(String frame);
    /** The connection was closed (code like the desktop RPC, e.g. 4000 = invalid client id). */
    void onClose(int code, String reason);
}
