# Hearthfolk

Your villagers have been standing around with their arms crossed since the day you found them. Give them a reason.

Hearthfolk is a NeoForge mod for Minecraft 26.3 that makes vanilla villagers work. Build a farm and the farmer works it. Dig a mine and a miner climbs down into it. They earn emeralds, trade with each other, eat, have children, fight raiders and carry goods between villages on pack mules. No new mob, no menus.

The page on Modrinth is written from `docs/modrinth/description.md`, and changing that file and merging it updates the page.

## Building from source

```bash
./gradlew build
./gradlew runClient
./gradlew runGameTestServer
```

Requires Java 25. The mod id is `minenow` (the mod was called MineNow before it was renamed), and the jar is built as `minenow-<version>.jar`.

## Requirements

- Minecraft 26.3
- NeoForge 26.3 or newer

## License

See [LICENSE.txt](LICENSE.txt).
