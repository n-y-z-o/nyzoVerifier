package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class WhitelistResponse extends BooleanMessageResponse {

    private WhitelistResponse(boolean accepted, String message) {
        super(accepted, message);
    }

    public static WhitelistResponse forRequest(Message whitelistRequest) {
        // Some of this logic is redundant with external checks. This improves robustness.
        boolean accepted = false;
        String message;
        if (whitelistRequest == null) {
            message = "request is null";
        } else if (whitelistRequest.getType() != MessageType.WhitelistRequest424) {
            message = "request is incorrect type";
        } else if (!(whitelistRequest.getContent() instanceof IpAddressMessageObject)) {
            message = "IP address not provided in request";
        } else if (!ByteUtil.arraysAreEqual(whitelistRequest.getSourceNodeIdentifier(), Verifier.getIdentifier())) {
            message = "requester, " + PrintUtil.compactPrintByteArray(whitelistRequest.getSourceNodeIdentifier()) +
                    " is not local verifier, " + PrintUtil.compactPrintByteArray(Verifier.getIdentifier());
        } else {
            // Whitelist the address, mark as accepted, and produce the response message.
            byte[] ipAddress = ((IpAddressMessageObject) whitelistRequest.getContent()).getIpAddress();
            Message.whitelistIpAddress(ipAddress);
            accepted = true;
            message = "accepted, added IP address " + IpUtil.addressAsString(ipAddress) + " to dynamic whitelist";
        }

        return new WhitelistResponse(accepted, message);
    }

    public static WhitelistResponse fromByteBuffer(ByteBuffer buffer) {
        BooleanMessageResponse response = BooleanMessageResponse.fromByteBuffer(buffer);
        return new WhitelistResponse(response.isSuccess(), response.getMessage());
    }

    @Override
    public String toString() {
        return "[WhitelistResponse: " + (isSuccess() ? "success" : "fail") + ", message=\"" + getMessage() + "\"]";
    }
}
