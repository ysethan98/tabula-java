package technology.tabula;

public class DebugOutput {
    private boolean debugEnabled;

    public DebugOutput(boolean debug) {
        this.debugEnabled = debug;
    }

    public void debug(String msg) {
        if (this.debugEnabled) {
            System.err.println(msg);
        }
    }
}