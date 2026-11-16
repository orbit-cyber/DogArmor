# DogArmor

A Paper plugin for **Minecraft 26.3** that makes dog armour upgradable.
Vanilla has exactly one dog armour item (`minecraft:wolf_armor`, 64 durability)
and no way to improve it - DogArmor turns it into a tier system:

**Leather → Copper → Iron → Golden → Diamond → Netherite** (fully configurable, add your own).

Wolf armour blocks damage while it lasts, so each tier's real benefit is its
durability: 64 → 110 → 160 → 200 → 300 → 500. Tiers can also carry their own
enchantments: **Diamond** comes with Mending, **Netherite** with Mending and
Unbreaking III. Dye colour is kept when a dog's armour is upgraded;
enchantments are not - each tier item carries exactly the enchantments listed
for that tier in `config.yml`. Repairing with armadillo scutes, anvils and
grinding work as in vanilla, because every tier item is still a wolf armour
item.

## Download

Grab the ready-built plugin - no toolchain needed:

**[Download the latest DogArmor.jar](https://github.com/orbit-cyber/DogArmor/releases/latest/download/DogArmor.jar)**

Drop it into your server's `plugins/` folder and restart. That link always
serves the newest build from `main`, because GitHub Actions rebuilds the jar on
every push and republishes it.

## How players use it

- Craft or find vanilla **wolf armour** (6 armadillo scutes) and equip it on your tamed dog.
- Right-click your dog while holding the next tier's material to upgrade it:

| Tier | Upgrade material | Cost |
|---|---|---|
| Copper Dog Armour | Copper ingot | 4 |
| Iron Dog Armour | Iron ingot | 4 |
| Golden Dog Armour | Gold ingot | 4 |
| Diamond Dog Armour | Diamond | 4 |
| Netherite Dog Armour | Netherite ingot | 1 |

- `/dogarmor info` - look at a dog and see its armour tier and durability.
- `/dogarmor upgrade` - upgrade the dog you are looking at (materials from your inventory).
- `/dogarmor give <tier>` - admin: get a tier's armour item (`leather`, `copper`, `iron`, `golden`, `diamond`, `netherite`).
- `/dogarmor reload` - admin: reload `config.yml`.

Aliases: `/dog`.

**Permissions:** `dogarmor.use` (default: everyone) to check/upgrade your own
dogs; `dogarmor.admin` (default: operators) for `give`, `reload` and other
players' dogs.

## Building

Requires **Java 25** and **Gradle 9+** (or an IDE with the Gradle integration):

```
gradle build
```

The plugin jar lands in `build/libs/DogArmor-1.0.0.jar`. Drop it into your
server's `plugins/` folder and restart. Paper 26.3 is resolved from the
PaperMC repository at build time.

## Config

`src/main/resources/config.yml` (copied to `plugins/DogArmor/config.yml` on
first run) defines the tier list in order. Every entry supports:

- `key` - tier name used by `/dogarmor give`
- `display-name` - MiniMessage item name (supports colours like `<#B87333>`)
- `lore` - MiniMessage lore lines
- `durability` - the tier's max durability (its actual gameplay benefit)
- `upgrade-material` - Bukkit material consumed to reach this tier (`NONE` for the base tier)
- `upgrade-cost` - how many of that material
- `rarity` - `COMMON`, `UNCOMMON`, `RARE` or `EPIC` (name colour)
- `glint` - whether the item gets an enchantment glint
- `enchantments` - enchantments every item of this tier carries, as
  `name: level` pairs (a list of `"name: level"` strings works too). Omit it for
  none; an unknown name fails the load with a console error rather than being
  skipped.

To add a tier, insert a new entry into the list, e.g. an emerald tier between
gold and diamond with `upgrade-material: EMERALD`. Messages are also fully
configurable under `messages:`.

## How it works

Vanilla 26.3 has no iron/diamond/netherite wolf armour items, so tiers are
built from the single `WOLF_ARMOR` material and marked with Paper data
components: `MAX_DAMAGE` (the tier durability), `RARITY`,
`ENCHANTMENT_GLINT_OVERRIDE`, the tier's enchantments, plus a persistent
`dogarmor:tier` marker so the plugin knows which tier a dog is wearing. Tiers
can only be applied in order, one upgrade at a time.


## Download

**[Latest DogArmor.jar](https://github.com/orbit-cyber/DogArmor/releases/latest/download/DogArmor.jar)**
- always the newest build from `main`.
