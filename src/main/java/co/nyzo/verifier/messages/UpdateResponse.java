package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class UpdateResponse implements MessageObject {

    private boolean accepted;
    private String message;

    public UpdateResponse(Message updateRequest) {

        System.out.println("processing update request");

        try {
            StringBuilder error = new StringBuilder();
            if (isValidUpdateRequest(updateRequest, error)) {
                accepted = true;
                message = "Update request accepted";
                update();
            } else {
                accepted = false;
                message = "Update request not accepted (error=\"" + error + "\")";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private UpdateResponse(boolean accepted, String message) {
        this.accepted = accepted;
        this.message = message;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.booleanField +       // accepted
                FieldByteSize.string(message);    // message
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(accepted ? (byte) 1 : (byte) 0);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    public static UpdateResponse fromByteBuffer(ByteBuffer buffer) {

        UpdateResponse result = null;

        try {
            boolean accepted = buffer.get() == 1;
            short messageByteLength = buffer.getShort();
            byte[] messageBytes = new byte[messageByteLength];
            buffer.get(messageBytes);
            String message = new String(messageBytes, StandardCharsets.UTF_8);

            result = new UpdateResponse(accepted, message);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    private static boolean isValidUpdateRequest(Message updateRequest, StringBuilder error) {

        boolean valid = true;
        if (!ByteUtil.arraysAreEqual(updateRequest.getSourceNodeIdentifier(), Verifier.getIdentifier())) {
            error.append("The identifier, " +
                    ByteUtil.arrayAsStringWithDashes(updateRequest.getSourceNodeIdentifier()) + ", is incorrect. ");
            valid = false;
        }

        if (!updateRequest.isValid()) {
            error.append("The signature is invalid. ");
            valid = false;
        }

        if (error.length() > 0 && error.charAt(error.length() - 1) == ' ') {
            error.deleteCharAt(error.length() - 1);
        }

        return valid;
    }

    private static void update() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Flag that the system should terminate and close the MeshListener socket.
                UpdateUtil.terminate();
                MeshListener.closeSockets();

                // Wait for the verifier and mesh listener to terminate.
                while (Verifier.isAlive() || MeshListener.isAlive()) {
                    try {
                        Thread.sleep(300L);
                        System.out.println("waiting for termination: v=" + (Verifier.isAlive() ? "alive" :
                                "terminated") + ", m=" + (MeshListener.isAlive() ? "alive" : "terminated"));
                    } catch (Exception ignored) { }
                }

                // Pull the latest code and compile.
                runProcess(new ProcessBuilder("git", "reset", "--hard", "HEAD"));
                runProcess(new ProcessBuilder("git", "pull", "origin", "master"));
                runProcess(new ProcessBuilder("./gradlew", "build"));
            }
        }).start();
    }

    @Override
    public String toString() {
        return "[UpdateResponse(" + (accepted ? "accepted" : "not accepted") + ", message=\"" + message + "\")]";
    }

    private static void runProcess(ProcessBuilder processBuilder) {

        try {
            processBuilder.directory(new File("/home/ubuntu/nyzoVerifier/"));
            Process process = processBuilder.start();
            readStream(process.getInputStream(), System.out);
            readStream(process.getErrorStream(), System.err);

            while (process.isAlive()) {
                try {
                    Thread.sleep(1000L);
                } catch (Exception e) {
                }
            }
        } catch (Exception ignored) { }
    }

    private static void readStream(InputStream inputStream, PrintStream outputStream) {

        BufferedReader outputReader = new BufferedReader(new InputStreamReader(inputStream));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        outputStream.println(line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
