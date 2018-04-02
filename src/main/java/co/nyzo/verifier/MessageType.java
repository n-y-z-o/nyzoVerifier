package co.nyzo.verifier;

import java.io.*;

public enum MessageType {

    Invalid0(0),
    NodeListRequest1(1),
    NodeListResponse2(2),
    NodeJoin3(3),
    NodeJoinAcknowledgement4(4),
    Transaction5(5),
    TransactionResponse6(6),
    PreviousHashRequest7(7),
    PreviousHashResponse8(8),
    NewBlock9(9),
    NewBlockAcknowledgement10(10),
    Ping200(200),
    PingResponse201(201),
    Unknown65535(65535);

    public static void main(String[] args) throws Exception {

        File targetFile = new File("src/main/resources/javascript/messageType.js");
        System.out.println("file exists: " + targetFile.exists() + " (" + targetFile.getAbsolutePath() + ")");

        targetFile.delete();
        BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile));
        for (MessageType messageType : values()) {
            writer.write("const " + messageType + " = " + messageType.getValue() + ";");
            writer.newLine();
        }
        writer.flush();
        writer.close();
    }

    private int value;

    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MessageType forValue(int value) {

        MessageType result = Unknown65535;
        for (MessageType type : values()) {
            if (value == type.value) {
                result = type;
            }
        }

        return result;
    }
}
