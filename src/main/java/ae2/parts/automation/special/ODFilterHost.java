package ae2.parts.automation.special;

public interface ODFilterHost {
    String getODFilter(boolean whitelist);

    void setODFilter(String expression, boolean whitelist);
}
