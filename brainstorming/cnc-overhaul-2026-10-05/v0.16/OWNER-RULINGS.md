# 0.16 owner rulings (2026-10-07)

Answered by the owner in session 5a50fb5a. They override DECISIONS.md where they differ.

- D1-D13: recommended answers (as DECISIONS.md records).
- D14: ALLOW. api 2.3 carries WantedCause.CONTACT, the ten Settings getters and the seven Messages constants.
- D15: admin regions live in cops-n-crooks (`cop_region`), exposed through RegionProvider/PlaceNames.
- D16: no evasion track counts as unseen for the phone and the [WANTED] sign.
- D17: recommended (accept the death reset for 0.16, document it, docket row for 0.18).
- D18: recommended (ring radius min(zone radius, 0.8 x Sight_Range)).
- **D19: ALTERNATIVE - ship a hospital respawn shield.** New settings.yml key
  `User.Death.Hospital.Shield_Seconds` (default 5; 0 = off). T3 adds the key (one more Settings getter in api 2.3, covered
  by D14) and its message if any; T8 grants the shield at the hospital respawn (incoming damage cancelled while active) and
  ends it early when the player attacks anything; new tests in T8 (shield blocks damage, expires, cancelled by attacking,
  0 disables). T19/T20 document it.
- D20: recommended (cap 3 crime-free takedowns per killer per rolling hour).
- D21: recommended (refuse to enable on a Keystone older than the pin).
- D22: recommended (bribe stars gated on unseen).
