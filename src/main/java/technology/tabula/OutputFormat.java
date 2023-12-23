package technology.tabula;

public enum OutputFormat {
    CSV, TSV, JSON;

    public static String[] formatNames() {
        OutputFormat[] values = OutputFormat.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }
}
