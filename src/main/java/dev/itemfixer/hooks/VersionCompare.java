package dev.itemfixer.hooks;

public final class VersionCompare {
    private VersionCompare() {
    }

    public static boolean isNewer(String remote, String local) {
        if (remote == null || remote.isBlank()) {
            return false;
        }
        String[] left = remote.split("\\.");
        String[] right = local.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            Integer next = digits(i < left.length ? left[i] : "0");
            Integer mine = digits(i < right.length ? right[i] : "0");
            if (next == null || mine == null) {
                return !remote.equals(local);
            }
            if (!next.equals(mine)) {
                return next > mine;
            }
        }
        return false;
    }

    private static Integer digits(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        return Integer.valueOf(part.substring(0, end));
    }

    public static void main(String[] args) {
        assert isNewer("1.0.1", "1.0.0");
        assert isNewer("1.0.10", "1.0.9");
        assert isNewer("1.1", "1.0.9");
        assert isNewer("2.0.0", "1.9.9");
        assert isNewer("1.0.1-beta", "1.0.0");
        assert !isNewer("1.0.0", "1.0.0");
        assert !isNewer("1.0.0", "1.0.1");
        assert !isNewer("1.0.9", "1.0.10");
        assert !isNewer("1.0.0-beta", "1.0.0-beta");
        assert !isNewer("", "1.0.0");
        assert !isNewer(null, "1.0.0");
        assert digits("12") == 12;
        assert digits("0-beta") == 0;
        assert digits("x") == null;
    }
}
