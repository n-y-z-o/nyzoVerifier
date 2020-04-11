package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.BooleanMessageResponse;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class BlockDelayResponse extends BooleanMessageResponse {

    private BlockDelayResponse(boolean accepted, String message) {
        super(accepted, message);
    }

    public static BlockDelayResponse forRequest(Message delayRequest) {
        // Some of this logic is redundant with external checks. This improves robustness.
        boolean accepted = false;
        String message;
        if (delayRequest == null) {
            message = "request is null";
        } else if (delayRequest.getType() != MessageType.BlockDelayRequest422) {
            message = "request is incorrect type";
        } else if (!ByteUtil.arraysAreEqual(delayRequest.getSourceNodeIdentifier(), Verifier.getIdentifier())) {
            message = "requester, " + PrintUtil.compactPrintByteArray(delayRequest.getSourceNodeIdentifier()) +
                    " is not local verifier, " + PrintUtil.compactPrintByteArray(Verifier.getIdentifier());
        } else {
            // Set the delay height, mark as accepted, and provide the new delay height in the response message.
            Block.setBlockDelayHeight();
            accepted = true;
            message = "accepted, sentinel test height is now " + Block.getBlockDelayHeight();
        }

        return new BlockDelayResponse(accepted, message);
    }

    public static BlockDelayResponse fromByteBuffer(ByteBuffer buffer) {
        BooleanMessageResponse response = BooleanMessageResponse.fromByteBuffer(buffer);
        return new BlockDelayResponse(response.isSuccess(), response.getMessage());
    }

    @Override
    public String toString() {
        return "[BlockDelayResponse(" + (isSuccess() ? "success" : "fail") + ", message=\"" + getMessage() + "\")]";
    }
}
