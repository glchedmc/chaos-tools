# Chaos Tools (Fabric, MC 26.2, official mappings)

Custom enchantments with deep, chaotic mechanics - your own spin on the
genre, not reskins of anyone else's specific ideas. Server-authoritative:
all the actual gameplay logic runs via server-only events, so it can't be
spoofed client-side. All effects are enchantments applied to normal
vanilla items (wood to netherite), so there's no custom art needed at all
- another advantage of the enchantment approach over unique items.

## Current status: 4 of 11 enchantments built

**Built:** Debt (confirmed working in-game), Diet, Permit, Consequence.
**Planned next:** Gravity Pickaxe, then the echo shard items and blocks.

## Debt

Mining tool enchantment, levels 1-50. **A genuine curse, not a buff**: at
enchantment level N, you need N banked "credits" - from mining worthless
blocks (dirt/sand/gravel/etc.) - before you're allowed to mine even ONE
valuable block. The "1 good block" side of the ratio never changes; only
the cost scales with level. Level 1 = 1:1 (mildly annoying). Level 50 =
mine 50 worthless blocks just to unlock a single valuable one. Credits
are spent in full each time you mine a valuable block.

**Assumption made, not something you specified**: while you're "in the
hole" and holding the tool, it slowly drains your hunger every couple
seconds - a passive cost for being stuck, not just a one-time gate.

## Permit (replaces the original Diplomat's Hoe idea)

Mining tool enchantment (pickaxe/shovel), levels 1-10. Every block-break
attempt is a dice roll - approved or denied, no persistent state at all,
the simplest of the enchantments so far. Approval chance = 55 + 4 x level,
capped at 95%. Level 1 sits around 59%, level 10 is a near-certain 95%.

## Diet

Sword enchantment, levels 1-10. Every hit shrinks the target using
Minecraft's real `scale` attribute (the same one behind slime size) - a
genuine data-driven mechanic, not custom rendering, so it sidesteps the
rendering-pipeline pain from earlier mods this session. Shrink per hit
scales with level (0.03 x level, floor of 0.15) but is flat regardless of
how much damage that particular hit dealt. On death from a Diet weapon,
the loot table result gets capped down to a single item.

**Design choice:** capped Diet's max level at 10 instead of 50 - a flat
per-hit shrink scaling to 50 would trivialize the effect in one hit.

## Consequence

Compass enchantment, levels 1-5. Right-click scans a 32-block radius for
the nearest hostile mob and points the compass at it - using vanilla's
own **Lodestone Tracker** component, the same mechanic a real
lodestone-linked compass uses. That's a nice shortcut: we get needle-
pointing rendering entirely for free instead of writing any custom
rendering code. It's a snapshot, not a live track - the needle points at
where the danger *was* when you checked, so it drifts if you or the mob
move afterward. The twist: checking gives the found mob a temporary Speed
boost, scaled by enchant level - so getting the information makes the
situation a little worse, on purpose.

## Java/architecture concepts covered so far

- **Reading a stack trace**: chained exceptions show as multiple `Caused
  by:` blocks - the *last* one is the real root cause. Read it bottom-up
  as a call chain: each frame is "who called who."
- **`<clinit>` / `<init>`**: Java's internal names for a class's *static
  initializer* (runs once, sets up `static` fields) and *constructor*
  respectively.
- **Builder pattern**: `Item.Properties` is a builder - some builder
  methods (like `.setId()`) need to run *before* the object that consumes
  the builder is actually constructed, not just before it's registered.
- **Static vs. dynamic registries**: `Item`s live in a static registry -
  your Java code registers them by hand at startup. `Enchantment`s live
  in a *dynamic* (data-driven) registry - the real definition is a JSON
  file, loaded when a world starts. Java code just holds a `ResourceKey`
  (a typed "pointer" by name) and looks the real thing up via
  `level.registryAccess()` whenever it needs it.
- **Attributes**: entities have real, built-in numeric stats beyond just
  health - `Attributes.SCALE` controls visual size. Modding with an
  existing attribute (change a number Minecraft already understands) is
  much lower-risk than writing custom rendering code from scratch.
- **Reusing existing mechanics**: Consequence's compass-pointing reuses
  the same Lodestone Tracker data component vanilla already has, instead
  of building custom needle rendering. Worth internalizing as a general
  principle: before writing something custom, check whether the game
  already has a mechanic that does 90% of what you want.

## Building

1. Open `chaostools/` in IntelliJ as a Gradle project, let it import.
2. `./gradlew build` -> jar in `build/libs/`.
3. This is a normal (not client-only) mod - it needs to go in the
   **server's** `mods` folder for multiplayer, or your normal client
   `mods` folder for singleplayer (the integrated server counts).
4. `./gradlew runClient` to test in a dev environment.

## Risk areas (not fully cross-checked)

Confirmed against real docs or real testing: enchantment JSON shape,
dynamic registry lookup pattern, `LootContextParams.DIRECT_ATTACKING_ENTITY`
(confirmed real name after two wrong guesses - see git history/chat log).

Still-unverified pieces:

- `ServerLivingEntityEvents.ALLOW_DAMAGE` for hooking "a hit is about to
  land" - it's designed as an allow/deny filter, not specifically an
  "after damage" hook, but there's no confirmed dedicated after-damage
  event name to fall back on.
- `Attributes.SCALE` / `entity.getAttribute(...)` - moderate confidence,
  existed since roughly 1.20.5, not confirmed for 26.2 specifically.
- `LootTableEvents.MODIFY_DROPS` lambda signature `(key, context, drops)`
  - inferred, not cross-checked.
- `LootContextParams.THIS_ENTITY` - not yet hit a compile error on this
  one, so probably correct, but not independently confirmed the way
  `DIRECT_ATTACKING_ENTITY` was.
- New from Consequence: `UseItemCallback.EVENT` (right-click-in-air
  hook), `DataComponents.LODESTONE_TRACKER` + the `LodestoneTracker`
  record shape `(Optional<GlobalPos>, boolean)`, `GlobalPos.of(...)`,
  and `MobEffects.SPEED` (older versions used `MOVEMENT_SPEED` - this
  may have been renamed at some point, unconfirmed which name 26.2 uses).

If any of these are wrong, the compiler error (for the Java ones) or an
in-game "unknown tag" warning (for the JSON one) will point at exactly
which piece needs a different name - same troubleshooting approach we've
used for everything else this session.
