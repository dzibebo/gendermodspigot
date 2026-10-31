# Plugin for Wildfire's Female Gender Mod

This is a Spigot plugin that allows clients using [Wildfire's Female Gender Mod](https://modrinth.com/mod/female-gender)
to have synced configs when playing on a Spigot server. This plugin was made by me as a member of the community and is
not affiliated with the Wildfire's Female Gender Mod.

Wildfire's Female Gender Mod is still required on the client to use the features, all this plugin does is sync the
player-specific settings as it would on a Fabric server with the mod installed.

The plugin supports the Fabric and Forge transports used by compatible versions of the mod.

Thank you to Flamgop for the help with learning how to make a plugin, and Stigstille + winnpixie for porting it to the
latest Spigot version!

Download from Modrinth: https://modrinth.com/plugin/female-gender-spigot

## Build Instructions

1. (Optional) Open the project in your IDE of choice (i.e. Eclipse, IntelliJ IDEA, NetBeans, etc.)
2. Compile and run the tests with `mvn clean verify`.
3. Copy the JAR file from the `target` folder to your server's `plugins` directory.
4. Enjoy synced gender settings!

## Configuration Help

### Mod

`protocol` (Which packet format to use)

| Protocol |      Mod      |
|:--------:|:-------------:|
|    2     | 2.8.1 - 3.0.1 |
|    3     | 3.1.0 - 4.0.0 |
|    4     | 4.0.1 - 4.3.4 |
|    5     | 5.0.0-Beta.4  |

Setting this value to -1 will try using the newest known protocol, useful so you don't need to change this manually
every time you update the mod and plugin.

Protocol V5 support is verified against `5.0.0-Beta.4`, including its play-phase hello handshake. Future handshake
revisions are not assumed to be compatible until they have been tested.
