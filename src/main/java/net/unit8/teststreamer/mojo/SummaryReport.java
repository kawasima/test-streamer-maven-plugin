package net.unit8.teststreamer.mojo;

/**
 * Stands for summary report.
 *
 * @author kawasima
 */
public class SummaryReport {
    private int tests;
    private int failures;
    private int errors;
    private int skipped;
    private float elapsedTime;

    public SummaryReport() {
        tests = 0;
        failures = 0;
        errors = 0;
        skipped = 0;
        elapsedTime = 0.0f;
    }
    public int getTests() {
        return tests;
    }

    public void setTests(int tests) {
        this.tests = tests;
    }

    public int getFailures() {
        return failures;
    }

    public void setFailures(int failures) {
        this.failures = failures;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public float getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(float elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    private String formatTime(float elapsedTime) {
        if (elapsedTime < 60) {
            return elapsedTime + " seconds";
        } else if (elapsedTime < 60 * 60) {
            return String.format("%.0f minutes %.0f seconds", elapsedTime / 60, (elapsedTime % 60));
        } else {
            return String.format("%.0f minutes %.0f seconds", elapsedTime / 60, (elapsedTime % 60));
        }
    }

    public boolean isErrorFree() {
        return getFailures() == 0 && getErrors() == 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("Run: ").append(tests)
                .append(", Failed: ").append(failures)
                .append(", Errors: ").append(errors)
                .append("\nTotal time: ").append(formatTime(elapsedTime));
        return sb.toString();
    }
}
