package com.innercosmos.ai.goodbye;

/**
 * Result of the goodbye flow.
 * @param success whether the goodbye was triggered successfully
 * @param line the goodbye line spoken by Aurora
 * @param stepsCompleted list of completed async steps
 * @param confirmed whether user confirmed medium-confidence goodbye
 * @param reverted whether goodbye was reverted
 * @param confidence confidence level of the goodbye trigger
 */
public class GoodbyeResult {
    public boolean success;
    public String line;
    public java.util.List<String> stepsCompleted;
    public boolean confirmed;
    public boolean reverted;
    public double confidence;
    public String goodbyeStrength; // HIGH | MEDIUM | NONE

    public GoodbyeResult() {}

    public GoodbyeResult(boolean success, String line, java.util.List<String> stepsCompleted,
                         boolean confirmed, boolean reverted, double confidence) {
        this.success = success;
        this.line = line;
        this.stepsCompleted = stepsCompleted;
        this.confirmed = confirmed;
        this.reverted = reverted;
        this.confidence = confidence;
        this.goodbyeStrength = "NONE";
    }

    // Getters for compatibility with existing code
    public boolean success() { return success; }
    public String line() { return line; }
    public java.util.List<String> stepsCompleted() { return stepsCompleted; }
    public boolean confirmed() { return confirmed; }
    public boolean reverted() { return reverted; }
    public double confidence() { return confidence; }
    public String goodbyeStrength() { return goodbyeStrength; }
}