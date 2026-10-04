package io.github.creepydutchboy.covebattle.update;

/**
 * Tolerant version comparison. Compares dot-separated numeric segments, then falls back to a
 * string compare of any trailing suffix so that 1.0.0 &lt; 1.0.1 and 1.0.0-rc1 &lt; 1.0.0.
 */
public final class Semver {

    private Semver() {}

    /** Strips a leading {@code v} and surrounding whitespace, e.g. {@code v1.2.3} to {@code 1.2.3}. */
    public static String normalize(String version) {
        if (version == null) return "";
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v.trim();
    }

    /** Negative if a &lt; b, zero if equal, positive if a &gt; b. */
    public static int compare(String a, String b) {
        String va = normalize(a);
        String vb = normalize(b);

        String coreA = core(va);
        String coreB = core(vb);
        String suffixA = va.substring(coreA.length());
        String suffixB = vb.substring(coreB.length());

        String[] partsA = coreA.isEmpty() ? new String[0] : coreA.split("\\.");
        String[] partsB = coreB.isEmpty() ? new String[0] : coreB.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int na = i < partsA.length ? parse(partsA[i]) : 0;
            int nb = i < partsB.length ? parse(partsB[i]) : 0;
            if (na != nb) return Integer.compare(na, nb);
        }

        // No suffix (a release) outranks a suffix (a prerelease) at the same numeric version.
        boolean emptyA = suffixA.isEmpty();
        boolean emptyB = suffixB.isEmpty();
        if (emptyA != emptyB) return emptyA ? 1 : -1;
        return suffixA.compareToIgnoreCase(suffixB);
    }

    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static String core(String v) {
        int i = 0;
        while (i < v.length() && (Character.isDigit(v.charAt(i)) || v.charAt(i) == '.')) i++;
        String c = v.substring(0, i);
        while (c.endsWith(".")) c = c.substring(0, c.length() - 1);
        return c;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
