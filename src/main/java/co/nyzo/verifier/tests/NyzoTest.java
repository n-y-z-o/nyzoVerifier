package co.nyzo.verifier.tests;

public interface NyzoTest {
    boolean run();
    String getFailureCause();
}
