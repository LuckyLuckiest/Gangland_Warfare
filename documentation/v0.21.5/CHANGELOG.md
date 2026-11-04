# 0.21.5 Changelog - Disguise fix

[← Back to Documentation Index](../README.md)

Read [migration-0.21.5.md](../migration-0.21.5.md) before you start the server. It lists the hand edits an existing
server needs. Upgrade from 0.21.4. Config files are never rewritten: edit them by hand.

## Requires

- **Keystone 1.15.5** (unchanged).
- **gangland-api 2.7 and module `Host_Api` 2.7** (unchanged). Only the `cops-n-crooks` module changes.

## Changed

### The description says what the cops see

- **Every worn piece is named.** The radio line lists headwear, chest, legs and boots, the weapon in your hand, and
  on foot or in a car, for example "Suspect is masked in a carved pumpkin, wearing red jacket, armed with AK-47, on foot."
  Before, only a mask or the chestplate was named, and the line often read "Suspect is suspect".
- **Items are named by what they are.** The Bartizan item name comes first, so a police vest is a "police vest", not an
  "iron chestplate". Armour display names are ignored.
- **The first 911 call of a chase names the real description,** never "unknown suspect".
- **Spanish.** New `wanted_messages_es.yml`; the radio line becomes "Sospechoso: ...". Item and dye words stay English.

### The disguise works as a disguise

- **Recognition is live sight.** A cop of the chasing group must see you now and know your look, or have watched you
  change. The old 1.5 s squad timer no longer switches the disguise off, lifts the pursuit range or waives the cuff gate.
- **`Changes_Needed` (2) replaces `Match_Needed`.** Out of sight, change two of head, chest, legs, boots and on foot / in a
  car and the cops stop recognising you. The weapon is described but never counts. An old `Match_Needed` is ignored with a
  warning.
- **The police outfit** fools a fresh squad at once and is blown for the whole chase by a crime in it, or by a cop seeing
  you put it on. Taking a piece off and on again does not reset it.
- **Posts are fooled too.** Perimeter posts, the Marksman and standoff posts no longer see through a disguise.
- **The look is taken at the crime.** A crime reported through the witness gate records what you wore when you did it,
  not when the report lands seconds later.
- **An escape keeps the description,** so the post-escape search no longer re-stars any outfit.

### The cops tell you

- **Chat lines, once per change,** only while a cop of the chase can see you: "The cops recognise you: ...", "The cops
  don't recognise you.", and "In the police outfit, the cops take you for one of their own." Set a line to `''` to turn it
  off. New debug state `DISGUISE <player> state=seen`.

## Fixed

- A player nobody hunts any more (0 stars, no search) was re-described, re-radioed and filtered by a stale description,
  which made beat cops skip brandish warnings (docket 411, 430).
- Taking one piece of a blown police outfit off for a moment un-blew it (row 848).

## Docket

Fixed: 333 (posts), 354, 411, 430, 526, 841, 842, 843, 844, 846, 847, 848, 849, 850, 851. Re-ruled: 346, 351 (the cuff
gate is recognition). Open: 845 (an ungated crime nobody saw still records your look), 852 (item and dye words are
English).

## Decisions

- Zero grace: one AI tick (0.5 s) with no group cop seeing you drops recognition.
- The weapon is in the line, so drawing one in view makes the cops radio again, at most once per `Suspect_Description`
  cooldown (1200 ticks).
- Blown lasts the whole chase and clears when nobody hunts you any more, never at a quit.

## For developers

- gangland-api, Keystone and Bartizan are unchanged. `DisguiseSettings.matchNeeded` is now `changesNeeded`;
  `CopPerception.pursuitRange` no longer takes a squad. See [developer/cops-n-crooks.md](../developer/cops-n-crooks.md).
