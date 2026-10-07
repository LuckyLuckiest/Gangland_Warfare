# Re-review — WS8 G2+G3 fix round 1 — 2026-09-20 (Sonnet, transcribed)
F1 ADDRESSED (GrappleLaunchListener.java:84-90, called :69; tests :237-293; red recorded) · F2 ADDRESSED (GrappleService.java:135-136 isChunkLoaded; test restubbed; plan §0d) · F3 ADDRESSED (GrappleService.java:78-83 forget; GrappleAbortListener.java:79-82 onQuit; tests) · minors ADDRESSED (Arrival_Distance clamp :119; @CustomLog gone; header; class doc; direction assertion :172-175).
New breakage: none (same-frame world check; tickAll snapshots + isOnline guard; >> 4 sign-extends correctly).
Verdict: all addressed.
