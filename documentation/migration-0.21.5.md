# Migrating a server from 0.21.4 to Gangland 0.21.5

[← Back to Documentation Index](./README.md)

0.21.5 fixes the disguise and the suspect description from the playtest: the radio names what the suspect wears, a
disguise is judged by what the cops see right now, and the cops tell you whether they know you. A new install gets
everything from the shipped files. **An existing server keeps its old files**, and the code never rewrites them. The new
message keys work without any edit, because the code supplies their defaults; the hand edits below are for the keys
that changed meaning.

## 1. Swap the jars

- Replace the Gangland core jar with the 0.21.5 build and the `cops-n-crooks` module jar in
  `plugins/Gangland_Warfare/modules/` with the 0.21.5 build. Only `cops-n-crooks` changed; swapping every module jar
  together is harmless.
- Keystone stays at 1.15.5, Bartizan at 0.6.0, `Host_Api` at 2.7.

## 2. Hand edit: `Match_Needed` becomes `Changes_Needed` (`copsncrooks/cops.yml`)

The rule is now counted the other way round, over five pieces (head, chest, legs, boots, on foot / in a car) instead of
three. An old `Match_Needed` line is **ignored with a console warning**; it is not converted for you.

Under `Cops` -> `Disguise`, delete `Match_Needed` and add:

```yaml
      Changes_Needed: 2
```

- `Changes_Needed = 4 - Match_Needed`, so the default `Match_Needed: 2` becomes `Changes_Needed: 2` and keeps the same
  difficulty. A higher value is harder to shake the cops.
- If you do not edit, the default 2 applies.

## 3. Hand edit: the Spanish radio line (`copsncrooks/cop_radio_messages_es.yml`)

The suspect is now a whole phrase, so the old template reads wrong ("El sospechoso es sin nada llamativo"). Change the
`Suspect_Description` line (the shipped file already has it) to:

```yaml
   Suspect_Description:
      - "Sospechoso: %suspect%."
```

Keep your own wording around `%suspect%` if you changed the old line. The English `cop_radio_messages.yml` is unchanged.
`Caller_911` keeps its `Suspect: %suspect%` text in both languages.

## 4. Dead keys in `copsncrooks/wanted_messages.yml`

`Suspect.Bare`, `Suspect.Mask` and `Suspect.Format` are no longer read. Delete them from your file, or leave them: they do
nothing. Their replacements are `Suspect.Masked`, `Suspect.Wearing`, `Suspect.Plain` and `Suspect.Armed`; `Suspect.Unknown`,
`Suspect.On_Foot` and `Suspect.In_Car` stay. If you customised the old words, re-apply them to the new keys.

New keys (the code defaults apply until you add them; copy the blocks from the shipped file to customise):

| Key | Default | Meaning |
|---|---|---|
| `Suspect.Masked` | `masked in a %head%` | A masked suspect; `%head%` is the mask. |
| `Suspect.Wearing` | `wearing %items%` | What he wears; `%items%` lists head, chest, legs and boots. |
| `Suspect.Plain` | `in plain clothes` | He wears nothing the cops can describe. |
| `Suspect.Armed` | `armed with %weapon%` | The weapon in his hand. |
| `Disguise.Recognised` | `&cThe cops recognise you: &7%suspect%` | Chat line when a cop of the chase sees and knows you. |
| `Disguise.Unrecognised` | `&aThe cops don't recognise you.` | A cop sees you, but you no longer match. |
| `Disguise.Outfit` | `&9In the police outfit, the cops take you for one of their own.` | The police outfit holds. |

A `Disguise.*` or `Suspect.*` line set to `''` turns that line (or part) off. There is no separate switch.

## 5. `wanted_messages_es.yml` is new

`copsncrooks/wanted_messages_es.yml` is copied out of the module jar on the first start (it is not in a 0.21.4 install).
With `Settings.Language: es` the suspect words and the three `Disguise` lines are Spanish. Item and dye words ("iron
chestplate", "red jacket") stay English; that is docket row 852. A key missing from this file falls back to the English
file.

## 6. Behaviour that changed

- **The description names every piece.** Headwear, chest, legs, boots and the weapon, by the Bartizan item name first
  (`police vest`, not "iron chestplate"). "Suspect is suspect" is gone. The weapon is spoken but never counts as a change.
- **Recognition is live sight.** The 1.5 s squad timer no longer switches a disguise off, lifts the pursuit range or
  waives the cuff gate. A cop of the chasing group must see you now and know you (or have watched you change). One
  blind AI tick (0.5 s) drops recognition. A watched suspect is still cuffed.
- **The police outfit** fools a freshly dispatched squad at once, and is blown for the rest of the chase (also the
  post-escape search) by a crime in it or by a cop seeing you put it on.
- **Perimeter posts, the Marksman and standoff posts** are fooled by a disguise like any cop: no sighting, no `Eyes_On`,
  no `Overwatch_Spotted`. Tracker tags still use raw sight.
- **A gated crime's report** records the look you had at the crime, not when the 3 s / 8 s report lands.
- **An escape keeps the description** through the post-escape search. At 0 stars with no search, the description is
  neither re-learned nor re-radioed.
- **The first 911 call** of a chase names the real description.
- **Chat feedback** (section 4): one line per change, only while a cop of the chase can see you.
- **Debug:** `DISGUISE <player> state=seen` is new.
