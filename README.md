# Chaos Tools (Fabric, MC 26.2, official mappings)

Custom items with deep, chaotic mechanics - your own spin on the genre,
not reskins of anyone else's specific ideas. Server-authoritative: all the
actual gameplay logic runs via server-only events, so it can't be spoofed
client-side.

## Current status: Debt reworked as an enchantment (ratio-gate curse)

Debt is an enchantment (`data/chaostools/enchantment/debt.json`) applicable
to any mining tool, wood to netherite. **It's a genuine curse, not a
buff**: at enchantment level N, you need N banked "credits" - from mining
worthless blocks (dirt/sand/gravel/etc.) - before you're allowed to mine
even ONE valuable block. The "1 good block" side of the ratio never
changes; only the cost scales with level. Level 1 = 1:1 (mildly annoying).
Level 50 = mine 50 worthless blocks just to unlock a single valuable one.
Credits are spent in full each time you mine a valuable block, so you're
constantly re-earning your way back to zero.

**Assumption I made, not something you specified**: while you're "in the
hole" (below the required credit threshold) and holding the tool, it
slowly drains your hunger every couple seconds - a passive cost for being
stuck, not just a one-time gate. Let me know if you don't want that part.

**Planned (same enchantment approach going forward):** Diplomat's Hoe,
Compass of Consequence, Gravity Pickaxe, then the echo shard items and
blocks.

## Diet (new)

Sword enchantment, levels 1-10. Every hit with a Diet-enchanted weapon
shrinks the target using Minecraft's real `scale` attribute (the same
one behind things like slime size) - a genuine data-driven mechanic, not
custom rendering, so it sidesteps all the rendering-pipeline pain from
earlier mods this session. Shrink per hit scales with level (0.03 x level,
floor of 0.15 so it never fully vanishes) but is flat regardless of how
much damage that particular hit dealt, per your spec. On death, if the
killing blow came from a Diet weapon, the normal loot table result gets
capped down to a single item (whatever it would have dropped, quantity 1).

**Design choice you should know about:** I capped Diet's max level at 10
instead of matching Debt's 50 - a flat per-hit shrink scaling all the way
to 50 would mean a single hit takes almost any mob to minimum size,
which felt like it'd trivialize the effect rather than make it funnier.
Easy to change if you want it to go higher.

## Java/architecture concepts covered so far

- **Reading a stack trace**: chained exceptions show as multiple `Caused
  by:` blocks - the *last* one is the real root cause. Read it bottom-up
  as a call chain: each frame is "who called who."
- **`<clinit>` / `<init>`**: Java's internal names for a class's *static
  initializer* (runs once, sets up `static` fields) and *constructor*
  respectively.
- **Builder pattern**: `Item.Properties` is a builder - some builder
  methods (like `.setId()`) need to run *before* the object that consumes
  the builder is actually constructed, not just before it's registered -
  construction and registration are separate steps.
- **Static vs. dynamic registries**: `Item`s live in a static registry -
  your Java code registers them by hand at startup, always available
  immediately. `Enchantment`s (since 1.21+) live in a *dynamic*
  (data-driven) registry - the real definition is a JSON file, loaded
  when a world starts (like loot tables or recipes). Java code doesn't
  construct the enchantment; it just holds a `ResourceKey` (a typed
  "pointer" to it by name) and looks it up via `level.registryAccess()`
  whenever it actually needs the real thing.

- **Attributes**: entities have real, built-in numeric stats beyond just
  health - `Attributes.SCALE` controls visual size and is the same system
  behind things like why a baby zombie is smaller than an adult one.
  Modding with an existing attribute (change a number Minecraft already
  understands) is much lower-risk than writing custom rendering code from
  scratch, which is why Diet uses it instead of trying to hack the mob's
  actual 3D model.

## Building

1. Open `chaostools/` in IntelliJ as a Gradle project, let it import.
2. `./gradlew build` -> jar in `build/libs/`.
3. This is a normal (not client-only) mod - it needs to go in the
   **server's** `mods` folder for multiplayer, or your normal client
   `mods` folder for singleplayer (the integrated server counts).
4. `./gradlew runClient` to test in a dev environment.

## Risk areas (not fully cross-checked)

Confirmed against real docs: enchantment JSON shape, `PickaxeItem`/`Tiers`
removal (moot now that Debt isn't a unique item), `ItemGroupEvents` ->
`CreativeModeTabEvents` (also moot, no more custom item to add to a
creative tab). New unverified pieces from today's enchantment rework:

- `level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)`
  - the exact method chain for looking up a dynamic-registry entry by
  its ResourceKey. Reasonably confident in shape, not cross-checked
  against a real source.
- `EnchantmentHelper.getItemEnchantmentLevel(Holder<Enchantment>,
  ItemStack)` - the helper for reading an enchantment's level off a
  stack. Long-standing pattern from earlier versions; not confirmed
  specifically for 26.2.
- `"#minecraft:enchantable/mining"` tag in the JSON - assumed to exist
  based on the confirmed `"#minecraft:enchantable/weapon"` pattern shown
  in Fabric's docs for a different enchantment example.

- `ServerLivingEntityEvents.ALLOW_DAMAGE` for hooking "a hit is about to
  land" - genuinely uncertain this is the right event (it's designed as
  an allow/deny filter, not specifically an "after damage" hook, but I
  don't have a confirmed source for a dedicated after-damage event name).
- `Attributes.SCALE` / `entity.getAttribute(...)` - moderate confidence,
  this attribute has existed since roughly 1.20.5 but not confirmed for
  26.2 specifically.
- `LootTableEvents.MODIFY_DROPS` (package `net.fabricmc.fabric.api.loot.v3`,
  matching the confirmed-present `fabric-loot-api-v3` module) and its
  exact lambda signature `(key, context, drops)` - inferred, not
  cross-checked.
- `LootContextParams.THIS_ENTITY` / `KILLER_ENTITY` for reading who died
  and who killed them out of the loot context - long-standing names in
  past versions, not confirmed for 26.2.

If any of these are wrong, the compiler error (for the Java ones) or an
in-game "unknown tag" warning (for the JSON one) will point at exactly
which piece needs a different name.

## Placeholder art

The item currently reuses vanilla's netherite pickaxe texture
(`assets/chaostools/models/item/debt_pickaxe.json`) since I can't generate
custom pixel art here. Swap in real textures whenever you want -
`assets/chaostools/textures/item/debt_pickaxe.png` (16x16) plus updating
the model's texture reference.
