package net.aikeigroup.wakgcraft.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses %args%, %args-1%, %args-2%, etc. placeholders in format strings.
 *
 * Rules:
 * - %args-N% captures the Nth word (1-indexed) from the input arguments.
 * - %args% captures ALL remaining text after the highest positional arg consumed.
 * - If %args% is alone (no positional args in format), it captures everything.
 * - If a required positional arg is missing, returns null (signals usage error).
 */
public class ArgsParser {

    private static final Pattern POSITIONAL_PATTERN = Pattern.compile("%args-(\\d+)%");

    /**
     * Replaces %args-N% and %args% placeholders in a format string.
     *
     * @param format The format string containing placeholders.
     * @param rawArgs The raw argument string (everything after the command).
     * @return The parsed string, or null if required positional args are missing.
     */
    public static String parse(String format, String rawArgs) {
        if (format == null) return null;
        if (rawArgs == null) rawArgs = "";

        String[] words = rawArgs.isEmpty() ? new String[0] : rawArgs.split("\\s+");

        // Find the highest positional arg number in the format
        int maxPositional = 0;
        Matcher matcher = POSITIONAL_PATTERN.matcher(format);
        while (matcher.find()) {
            int n = Integer.parseInt(matcher.group(1));
            if (n > maxPositional) maxPositional = n;
        }

        // Check if we have enough words for all positional args
        if (maxPositional > 0 && words.length < maxPositional) {
            // Not enough arguments for the required positional placeholders
            return null;
        }

        // Replace positional args: %args-1%, %args-2%, etc.
        String result = format;
        for (int i = 1; i <= maxPositional; i++) {
            result = result.replace("%args-" + i + "%", words[i - 1]);
        }

        // Replace %args% with the remaining text after the last positional arg
        if (result.contains("%args%")) {
            String remaining;
            if (maxPositional > 0 && maxPositional < words.length) {
                // Build remaining from words after the last positional
                StringBuilder sb = new StringBuilder();
                for (int i = maxPositional; i < words.length; i++) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(words[i]);
                }
                remaining = sb.toString();
            } else if (maxPositional == 0) {
                // No positional args, %args% captures everything
                remaining = rawArgs;
            } else {
                // All words consumed by positional args, nothing left for %args%
                remaining = "";
            }
            result = result.replace("%args%", remaining);
        }

        return result;
    }

    /**
     * Counts how many required arguments a format string expects.
     * This is the max of: highest %args-N% number, or 1 if only %args% is present.
     *
     * @param format The format string.
     * @return The minimum number of arguments required, 0 if none.
     */
    public static int getRequiredArgCount(String format) {
        if (format == null) return 0;

        int maxPositional = 0;
        Matcher matcher = POSITIONAL_PATTERN.matcher(format);
        while (matcher.find()) {
            int n = Integer.parseInt(matcher.group(1));
            if (n > maxPositional) maxPositional = n;
        }
        return maxPositional;
    }
}
