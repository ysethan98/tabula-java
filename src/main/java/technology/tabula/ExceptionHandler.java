package technology.tabula;

public class ExceptionHandler {
    public static void handleException(String message, Exception e) {
        System.err.println(message);
        e.printStackTrace();
    }
}
