package vn.perfidanb.jarbe.util;

public final class HumanSize {
    private static final String[] UNITS = {"B", "KB", "MB", "GB"};

    private HumanSize() {
    }

    public static String format(long bytes) {
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        if (unit == 0) {
            return bytes + " B";
        }
        return String.format("%.2f %s", value, UNITS[unit]);
    }
}
