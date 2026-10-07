# Readability Verification: AUTO Drop_Mode Content

Verification lens: Plain words a server owner understands, consistent tone and layout with neighbouring cards, no unexplained jargon, YAML block-style with Capitalized_Underscore keys, phone-width safe (no horizontal overflow).

Lines verified: 775–782 (version entry), 892 (graph node), 945 (graph caption), 1650–1731 (feature article), 1662–1727 (how/examples/code), 2579–2617 (roadmap + decisions).

## Findings by Severity

### Blocker
None.

### Major
None.

### Minor

1. **Jargon: escape habit score range unexplained**  
   *Severity:* minor  
   *Location:* Line 1671, "What it learns" paragraph  
   *Evidence:* "an escape habit from −1 to 1" introduced without explanation of what the bounds mean to an admin. The text adds "his escapes against what the server expects for chases like his" but does not define what −1, 0, and 1 represent in plain terms.  
   *Fix:* Expand to: "an escape habit from −1 (always caught) to 1 (always escapes), measuring his escapes against what the server expects for chases like his".

2. **Unicode minus sign inconsistency**  
   *Severity:* minor  
   *Location:* Line 1671, "escape habit from −1 to 1"  
   *Evidence:* Uses U+2212 (minus sign) instead of hyphen-minus (U+002D). Other numeric ranges elsewhere use standard characters. Inconsistent rendering across old browsers or terminal fallback.  
   *Fix:* Change to "escape habit from -1 to 1" (hyphen-minus, matching rest of document).

3. **Nested code comment line breaks**  
   *Severity:* minor  
   *Location:* Lines 1724–1726, YAML comment block  
   *Evidence:* Comment "# plus Escape_Rate, Prior_Chases, Decay_Per_Chase, Habit_Time_Strength," wraps across three lines in the code block, making it hard to read. Comment continues: "# Min_Chase_Seconds, Min_Seconds_Between_Outcomes; and Repeat_Chases," and "# Repeat_Window_Minutes one level up". The semicolon breaks the sense.  
   *Fix:* Rewrite as two separate lines: "# plus Escape_Rate, Prior_Chases, Decay_Per_Chase, Habit_Time_Strength, Min_Chase_Seconds," then "# Min_Seconds_Between_Outcomes, Repeat_Chases, Repeat_Window_Minutes one level up" (remove semicolon, join the two groups).

4. **Reference to "the plan" without URL**  
   *Severity:* minor  
   *Location:* Line 1688, caption "Sketch of Wanted.Evasion.Auto in copsncrooks/wanted.yml (trimmed; the full 29-key block is in the plan)"  
   *Evidence:* Readers may not know where "the plan" is. For anyone opening just the page, this is opaque. A link or a more specific reference (e.g., "the full 29-key block is in brainstorming/auto-drop/PLAN.md") would help.  
   *Fix:* Change to: "Sketch of Wanted.Evasion.Auto in copsncrooks/wanted.yml (trimmed; the full config is in the implementation plan)".

5. **Phrasing: "clamped to 1 up to"**  
   *Severity:* minor  
   *Location:* Line 1662, "The first matching ending wins, and every result is clamped to 1 up to your current stars:"  
   *Evidence:* The phrase "clamped to 1 up to" is awkward. "Clamped between 1 and your current stars" is clearer and more standard.  
   *Fix:* Change to: "...every result is clamped between 1 and your current stars:".

## Summary

- **Blockers:** 0
- **Major:** 0
- **Minor:** 5

All findings are cosmetic or tone-related; no technical errors or broken links. The content reads clearly for server owners at phone width, YAML is valid block-style with correct key names, and explanations are concrete with worked examples (E1–E8, W1–W6). The five endings are clearly named and distinguished, and the learning mechanic is explained with enough detail for admins to understand what the tables track.
