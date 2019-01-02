package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.MeshResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;  

import java.util.ArrayList;  
import java.util.List;  
import java.util.concurrent.atomic.AtomicBoolean;

public class ScriptUtil {

    public static List<byte[]> ipAddressesForVerifier(byte[] identifier) {  
        
        // Ask Nyzo verifier 0 for the mesh. Get the IP addresses of the verifier.  
        List<byte[]> ipAddresses = new ArrayList<>();  
        Message meshRequest = new Message(MessageType.MeshRequest15, null);  
        AtomicBoolean receivedResponse = new AtomicBoolean(false);  
        Message.fetch("verifier0.nyzo.co", MeshListener.standardPort, meshRequest, new MessageCallback() {  
            
            @Override  
            public void responseReceived(Message message) {  
                
                MeshResponse meshResponse = (MeshResponse) message.getContent();  
                for (Node node : meshResponse.getMesh()) {  
                    if (ByteUtil.arraysAreEqual(identifier, node.getIdentifier())) {  
                        ipAddresses.add(node.getIpAddress());  
                    }  
                    
                    receivedResponse.set(true);  
                }  
            }  
        });  
        
        // Wait for the response to return.  
        while (!receivedResponse.get()) {  
            try {  
                Thread.sleep(300L);  
            } catch (Exception ignored) { }  
        }  
        
        if (ipAddresses.isEmpty()) {  
            System.out.println("unable to find IP addresses for identifier " +  
                    PrintUtil.compactPrintByteArray(identifier));  
        } else {  
            for (byte[] ipAddress : ipAddresses) {  
                System.out.println("found IP address: " + IpUtil.addressAsString(ipAddress));  
            }  
        }  
        
        return ipAddresses;  
    }  
}
