// Binder interface exported by the Discord Android app for the Discord Social SDK (Rich Presence over RPC).
// Package and method order must stay exactly like this - the descriptor and transaction IDs come from them.
// Interface definition taken from antisocialcord-kt (MIT, Copyright 2026 Nightdavisao), reverse-engineered from the Social SDK.
package com.discord.socialsdk.rpc;

import com.discord.socialsdk.rpc.IDiscordRpcCallback;
import com.discord.socialsdk.rpc.IDiscordRpcConnection;

interface IDiscordRpcService {
    /** applicationId, RPC protocol version ("1"), callback for frames from Discord. */
    IDiscordRpcConnection connect(long applicationId, String version, IDiscordRpcCallback callback);
}
