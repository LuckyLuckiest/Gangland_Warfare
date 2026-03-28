package me.luckyraven.sign;

/**
 * The point of having a sign type is to properly distinguish two possible values that can arise, which is the typed and
 * generated sign value.
 *
 * @param typed the first name that would be used to register the sign.
 * @param generated the name that will be used when generating and validating the instance of the sign.
 */
public record SignType(String typed, String generated) { }
