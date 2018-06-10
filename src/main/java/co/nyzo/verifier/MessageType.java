package co.nyzo.verifier;

import java.io.*;

public enum MessageType {

    // standard-operation messages
    Invalid0(0),
    BootstrapRequest1(1),
    BootstrapResponse2(2),
    NodeJoin3(3),
    NodeJoinResponse4(4),
    Transaction5(5),
    TransactionResponse6(6),
    PreviousHashRequest7(7),
    PreviousHashResponse8(8),
    NewBlock9(9),
    NewBlockResponse10(10),
    BlockRequest11(11),
    BlockResponse12(12),
    TransactionPoolRequest13(13),
    TransactionPoolResponse14(14),
    MeshRequest15(15),
    MeshResponse16(16),
    StatusRequest17(17),
    StatusResponse18(18),
    BlockVote19(19),
    BlockVoteResponse20(20),

    // test messages
    Ping200(200),
    PingResponse201(201),

    // maintenance messages
    UpdateRequest300(300),
    UpdateResponse301(301),

    // bootstrapping messages
    ResetRequest500(500),
    ResetResponse501(501),

    // the highest allowable message number is 65535
    IncomingRequest65533(65533),
    Error65534(65534),
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
