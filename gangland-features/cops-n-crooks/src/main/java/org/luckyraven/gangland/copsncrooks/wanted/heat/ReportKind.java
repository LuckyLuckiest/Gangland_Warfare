package org.luckyraven.gangland.copsncrooks.wanted.heat;

/**
 * Who reported a crime that landed late (0.21.2, PLAN.md section 3): stored on its {@link CrimeRecord} and named by the
 * star card's chat cause line ({@code Hud.Card.*}). A crime committed at once has no kind.
 *
 * @since 0.21.2
 */
public enum ReportKind {
	/** A cop eyewitness radioed it in. */
	COP,
	/** A civilian's 911 call landed. */
	CIVILIAN,
	/** Dispatch presumed it: the dead cop stopped answering while he hunted the killer. */
	DISPATCH,
	/** A cop spotted the killer near the scene of an unseen crime. */
	SCENE
}
