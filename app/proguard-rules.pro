# R8 rules of the app (release and staging builds), on top of proguard-android-optimize.txt and the rules the libraries
# bring along (kotlinx.serialization, kotlinx.coroutines, OkHttp, Compose, AndroidX). Manifest components (activity,
# service, receiver, Application) are kept by the rules AAPT2 generates from the manifest.

# Shrink and optimize, but keep the names: the recent log (bug reports) prints exception class names
# (e.javaClass.simpleName, "$e") and stack traces stay readable without a mapping file - CI does not keep one.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# Binder interfaces of the Discord app's Social SDK RPC service (app/src/main/aidl). The descriptor strings and the
# transaction codes must stay exactly as generated: Discord's side calls IDiscordRpcCallback through them, and the
# Stub / Proxy classes are what DiscordBinderTransport talks to.
-keep class com.discord.socialsdk.rpc.** { *; }

# The settings (SharedPreferences, export / import files) are Config as JSON. kotlinx.serialization's own rules keep
# generated serializers that are looked up; Config's is called directly, kept explicitly so a settings file always
# reads back with every field.
-keep class io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config { *; }
-keep class io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config$$serializer { *; }
-keep class io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config$Companion { *; }
