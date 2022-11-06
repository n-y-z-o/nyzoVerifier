package co.nyzo.verifier.nyzoScript;

import co.nyzo.verifier.Transaction;

import java.util.List;

public interface NyzoScript {
    NyzoScriptState update(NyzoScriptState inputState, List<Transaction> transactions);
}
