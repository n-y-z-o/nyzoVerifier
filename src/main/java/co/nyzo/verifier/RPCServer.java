package co.nyzo.verifier;

import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.util.SignatureUtil;
import co.nyzo.verifier.util.PrintUtil;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
// import org.apache.commons.codec.binary.Hex;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.Authenticator;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;

import java.util.Scanner;


public class RPCServer
{
    private final HttpServer http_server;
    private final Dispatcher dispatcher;

    public RPCServer(int listen_port)
            throws Exception
        {
            String listen_host = "0.0.0.0";

            http_server = HttpServer.create(new InetSocketAddress(listen_host, listen_port), 0);
            http_server.createContext("/", new RootHandler());

            http_server.setExecutor(new ThreadPoolExecutor(8, 8, 
                        10, TimeUnit.MINUTES, 
                        new LinkedBlockingQueue<Runnable>()));
            http_server.start();
            dispatcher = new Dispatcher();
            register(new EchoHandler());
            register(new InfoHandler());
            register(new RawTransactionHandler());
            register(new CycleHandler());
            register(new BlockHandler());
            register(new BalanceHandler());
            register(new BroadcastHandler());
            register(new AllTransactionsHandler());	
            register(new GetTransactionsHandler());
            register(new TransactionConfirmedHandler());
        }

    public void register(RequestHandler handler)
    {
        dispatcher.register(handler);
    }


    public class RootHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange t) throws IOException {
            ByteArrayOutputStream b_out = new ByteArrayOutputStream();
            PrintStream print_out = new PrintStream(b_out);

            int code = 200;
            boolean auth_ok=false;

            try
            {
                Scanner scan = new Scanner(t.getRequestBody());
                String line = scan.nextLine();
                scan.close();

                JSONRPC2Request req = JSONRPC2Request.parse(line);
                JSONRPC2Response resp = dispatcher.process(req, null);

                print_out.println(resp.toJSONString());

            }
            catch(Throwable e)
            {
                code=500;
                print_out.println(e);
                System.out.println("error handling rpc method " + e);
                e.printStackTrace();
            }

            byte[] data = b_out.toByteArray();
            t.sendResponseHeaders(code, data.length);
            OutputStream out = t.getResponseBody();
            out.write(data);
            out.close();
        }
    }

    public class EchoHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"echo"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            return new JSONRPC2Response(req.getID());
        }
    }
    public class InfoHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"info"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            reply.put("nickname", Verifier.getNickname());
            reply.put("block_creation_information", Verifier.getBlockCreationInformation());
            reply.put("frozen_edge", BlockManager.getFrozenEdgeHeight());
            reply.put("trailing_edge", BlockManager.getTrailingEdgeHeight());
            reply.put("retention_edge", BlockManager.getRetentionEdgeHeight());
            reply.put("cycle_length", BlockManager.currentCycleLength());
            reply.put("identifier", ByteUtil.arrayAsStringWithDashes(Verifier.getIdentifier()));
            reply.put("nyzo_string", NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(Verifier.getIdentifier())));
            reply.put("transaction_pool_size", TransactionPool.transactionPoolSize());
            reply.put("voting_pool_size", NodeManager.getMeshSizeForGenesisCycleVoting());
            reply.put("version", Version.getVersion());
            return new JSONRPC2Response(reply, req.getID());
        }
    }
    public class BalanceHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"balance"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            byte[] identifier = new byte[0];
            String identifierGet = (String) req.getNamedParams().get("identifier");
            String nyzoStringIdGet= (String) req.getNamedParams().get("nyzo_string");

            if (identifierGet != null){
                identifier = ByteUtil.byteArrayFromHexString(identifierGet, FieldByteSize.identifier);
            }else if (nyzoStringIdGet != null){
                identifier = NyzoStringEncoder.decode(nyzoStringIdGet).getBytes();
            }
            
            long height = BlockManager.getFrozenEdgeHeight();
            Block block = BlockManager.frozenBlockForHeight(height);
            BalanceList bl = BalanceListManager.balanceListForBlock(block);
            List<BalanceListItem> items = bl.getItems();
            reply.put("list_length", items.size());
            long balance = 0;
            for (BalanceListItem item : items) {
                if (Arrays.equals(item.getIdentifier(), identifier)) {
                   balance = item.getBalance();
                }
            }
            reply.put("balance", balance);
            return new JSONRPC2Response(reply, req.getID());
        }
    }
    public class AllTransactionsHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"alltransactions"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            reply.put("all_transactions", TransactionPool.allTransactions());
            reply.put("transactions_pool_size", TransactionPool.transactionPoolSize());
            return new JSONRPC2Response(reply, req.getID());
        }
    }
    public class GetTransactionsHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"gettransaction"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            int height = (int)(long) req.getNamedParams().get("height");
            reply.put("transaction", TransactionPool.transactionsForHeight(height));
            return new JSONRPC2Response(reply, req.getID());
        }
    }
    public class BroadcastHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"broadcast"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            String sTransaction = (String) req.getNamedParams().get("tx");
            ByteBuffer txData = ByteBuffer.wrap(ByteUtil.byteArrayFromHexString(sTransaction, sTransaction.length() / 2));
            Transaction tx = Transaction.fromByteBuffer(txData);

            long height = BlockManager.heightForTimestamp(tx.getTimestamp());
            reply.put("target_height", height);

            StringBuilder error = new StringBuilder();
            StringBuilder warning = new StringBuilder();

            Message msg = new Message(MessageType.Transaction5, tx);
            boolean addedToPool = TransactionPool.addTransaction(tx, error, warning);

            if (warning.length() > 0) {
                System.out.println(" (warning=\"" + warning.toString().trim() + "\")");
            }

            if (error.length() > 0) {
                System.out.println(" (error=\"" + error.toString().trim() + "\")");
            }

            List<Node> mesh = NodeManager.getMesh();
            for (Node node : mesh) {
                if (node.isActive() && BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                    String ipAddress = IpUtil.addressAsString(node.getIpAddress());
                    Message.fetchTcp(ipAddress, node.getPortTcp(), msg, new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {
                            System.out.println("tx broadcast response from " + ipAddress + " is " + message);
                        }
                    });
                }
            }
            return new JSONRPC2Response(reply, req.getID());
        }
    }
    public class RawTransactionHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"rawtransaction"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            long amount = (long) req.getNamedParams().get("amount");
            byte[] receiverIdentifier = new byte[0];
            byte[] senderIdentifier = new byte[0];


            String receiverIdentifierGet = (String) req.getNamedParams().get("receiver_identifier");
            String senderIdentifierGet = (String) req.getNamedParams().get("sender_identifier");
            
            String receiverNyzoStringGet = (String) req.getNamedParams().get("receiver_nyzo_string");
            String senderNyzoStringGet = (String) req.getNamedParams().get("sender_nyzo_string");

            if (receiverIdentifierGet != null){
                receiverIdentifier = ByteUtil.byteArrayFromHexString(receiverIdentifierGet, FieldByteSize.identifier);
            }else if (receiverNyzoStringGet != null){
                receiverIdentifier = NyzoStringEncoder.decode(receiverNyzoStringGet).getBytes();
            }

            if (senderIdentifierGet != null){
                senderIdentifier = ByteUtil.byteArrayFromHexString(senderIdentifierGet, FieldByteSize.identifier);
            }else if (senderNyzoStringGet != null){
                senderIdentifier = NyzoStringEncoder.decode(senderNyzoStringGet).getBytes();
            }
           
            
            String sData = (String) req.getNamedParams().get("sender_data");
            byte[] data = sData.getBytes();

            long timestamp = System.currentTimeMillis();
            Object oTimestamp = req.getNamedParams().get("timestamp");
            if (oTimestamp != null) {
                timestamp = (long) oTimestamp;
            }

            long previousHashHeight = BlockManager.getFrozenEdgeHeight();
            Object oPreviousHashHeight = req.getNamedParams().get("previous_hash_height");
            if (oPreviousHashHeight != null) {
                previousHashHeight = (long) oPreviousHashHeight;
            }

            byte[] previousBlockHash = BlockManager.frozenBlockForHeight(previousHashHeight).getHash();
            String sPreviousBlockHash = (String) req.getNamedParams().get("previous_block_hash");
            if (sPreviousBlockHash != null) {
                previousBlockHash = ByteUtil.byteArrayFromHexString(sPreviousBlockHash, FieldByteSize.hash);
            }

            String sSig = (String) req.getNamedParams().get("signature");
            byte[] signature = new byte[0];
            if (sSig != null) {
                signature = ByteUtil.byteArrayFromHexString(sSig, FieldByteSize.signature);
            }

            Transaction tx = Transaction.standardTransaction(
                    timestamp, amount, receiverIdentifier, previousHashHeight, previousBlockHash, senderIdentifier, data, signature);

            // For testing purposes we will sign a transaction if supplied a private seed
            String sPrivateSeed = (String) req.getNamedParams().get("private_seed");
            String sPrivateNyzoString = (String) req.getNamedParams().get("private_nyzo_string");

            if (sPrivateSeed != null || sPrivateNyzoString != null) {
                byte[] seed = new byte[0];
                if (sPrivateSeed != null){
                    seed = ByteUtil.byteArrayFromHexString(sPrivateSeed, FieldByteSize.identifier);
                }else if (sPrivateNyzoString != null){
                    seed = NyzoStringEncoder.decode(sPrivateNyzoString).getBytes();
                }
                
                signature = SignatureUtil.signBytes(tx.getBytes(true), seed);
                reply.put("signature", ByteUtil.arrayAsStringNoDashes(signature));
                tx = Transaction.standardTransaction(
                    timestamp, amount, receiverIdentifier, previousHashHeight, previousBlockHash, senderIdentifier, data, signature);
            }

            if (Boolean.TRUE.equals(req.getNamedParams().get("broadcast"))) {
                Message msg = new Message(MessageType.Transaction5, tx);
                
                StringBuilder error = new StringBuilder();
                StringBuilder warning = new StringBuilder();
                TransactionPool.addTransaction(tx, error, warning);

                if (warning.length() > 0) {
                    System.out.println(" (warning=\"" + warning.toString().trim() + "\")");
                }

                if (error.length() > 0) {
                    System.out.println(" (error=\"" + error.toString().trim() + "\")");
                }

                List<Node> mesh = NodeManager.getMesh();
                for (Node node : mesh) {
                    if (node.isActive() && BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                        String ipAddress = IpUtil.addressAsString(node.getIpAddress());
                        Message.fetchTcp(ipAddress, node.getPortTcp(), msg, new MessageCallback() {
                            @Override
                            public void responseReceived(Message message) {
                                System.out.println("tx broadcast response from " + ipAddress + " is " + message);
                            }
                        });
                    }
                }
            }

   

            reply.put("valid_signature", tx.signatureIsValid());

            StringBuilder validationError = new StringBuilder();
            StringBuilder validationWarning = new StringBuilder();
            boolean transactionValid = tx.performInitialValidation(validationError, validationWarning);
            
            if (transactionValid){        
                reply.put("scheduled_block", BlockManager.heightForTimestamp(tx.getTimestamp()));
            }

            reply.put("valid", transactionValid);
            reply.put("validation_error", validationError.toString());
            reply.put("validation_warning", validationWarning.toString());

            reply.put("id", ByteUtil.arrayAsStringWithDashes(HashUtil.doubleSHA256(tx.getBytes(true))));
            reply.put("amount", amount);
            reply.put("receiver_identifier", ByteUtil.arrayAsStringNoDashes(receiverIdentifier));
            reply.put("sender_identifier", ByteUtil.arrayAsStringNoDashes(senderIdentifier));
            reply.put("receiver_nyzo_string", NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(receiverIdentifier)));
            reply.put("sender_nyzo_string", NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(senderIdentifier)));
            reply.put("timestamp", timestamp);
            reply.put("sign_data", ByteUtil.arrayAsStringNoDashes(tx.getBytes(true)));
            reply.put("sender_data", ByteUtil.arrayAsStringNoDashes(data));
            reply.put("previous_hash_height", previousHashHeight);
            reply.put("previous_block_hash", ByteUtil.arrayAsStringNoDashes(previousBlockHash));
            reply.put("raw", ByteUtil.arrayAsStringNoDashes(tx.getBytes(false)));
            
            return new JSONRPC2Response(reply, req.getID());
        }
    }

    public class TransactionConfirmedHandler implements RequestHandler{
        public String[] handledRequests() 
        {
            return new String[]{"transactionconfirmed"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            String sTransaction = (String) req.getNamedParams().get("tx");
            ByteBuffer txData = ByteBuffer.wrap(ByteUtil.byteArrayFromHexString(sTransaction, sTransaction.length() / 2));
            Transaction tx = Transaction.fromByteBuffer(txData);

            long transactionHeight = BlockManager.heightForTimestamp(tx.getTimestamp());

            Block transactionBlock = BlockManager.frozenBlockForHeight(transactionHeight);
            if (transactionBlock == null) {
                reply.put("message", "unable to determine whether transaction was incorporated into the chain");
                reply.put("status", "unknown");
                reply.put("block", -1);
                reply.put("signature", PrintUtil.compactPrintByteArray(tx.getSignature()));
            } else {
                boolean transactionIsInChain = false;
                for (Transaction blockTransaction : transactionBlock.getTransactions()) {
                    if (ByteUtil.arraysAreEqual(blockTransaction.getSignature(), tx.getSignature())) {
                        transactionIsInChain = true;
                    }
                }

                if (transactionIsInChain) {
                    reply.put("message", "transaction is proceed in chain!");
                    reply.put("status", "proceed");
                    reply.put("block", transactionBlock.getBlockHeight() );
                    reply.put("signature", PrintUtil.compactPrintByteArray(tx.getSignature()));
                } else {
                    reply.put("message", "transaction is not proceed in chain!");
                    reply.put("status", "not_proceed");
                    reply.put("block", -1);
                    reply.put("signature", PrintUtil.compactPrintByteArray(tx.getSignature()));
                }
            }
            return new JSONRPC2Response(reply, req.getID());
        }
    }
    
    public class CycleHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"cycleinfo"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONArray nodes = new JSONArray();
            for (Node node : NodeManager.getMesh()) {
				JSONObject jNode = new JSONObject();
				jNode.put("address", IpUtil.addressAsString(node.getIpAddress()));
				jNode.put("queue_timestamp", node.getQueueTimestamp());
				jNode.put("port_tcp", node.getPortTcp());
				jNode.put("is_active", node.isActive());
                jNode.put("identifier", ByteUtil.arrayAsStringWithDashes(node.getIdentifier()));
                jNode.put("nyzo_string", NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(node.getIdentifier())));
				jNode.put("nickname", NicknameManager.get(node.getIdentifier()));

				nodes.add(jNode);
            }
            return new JSONRPC2Response(nodes, req.getID());
        }
    }
    public class BlockHandler implements RequestHandler
    {
        public String[] handledRequests() 
        {
            return new String[]{"block"};
        }

        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) 
        {
            JSONObject reply = new JSONObject();
            int height = (int)(long) req.getNamedParams().get("height");
            Block block = BlockManager.frozenBlockForHeight(height);
            if (block == null) {
                return new JSONRPC2Response(new JSONRPC2Error(500, "unknown block"), req.getID());
            }
            reply.put("height", block.getBlockHeight());
            reply.put("start_timestamp", block.getStartTimestamp());
            reply.put("verification_timestamp", block.getVerificationTimestamp());
            JSONArray txs = new JSONArray();
            boolean known = true;
            for (Transaction tx : block.getTransactions()) {
                JSONObject txObj = new JSONObject();
                txObj.put("type", ByteUtil.arrayAsStringNoDashes(tx.getType()));
                known = true;
                switch (tx.getType()) {
                    case Transaction.typeSeed:
                        txObj.put("type_enum", "seed");
                        break;
                    case Transaction.typeCoinGeneration:
                        txObj.put("type_enum", "coin_generation");
                        break;
                    case Transaction.typeStandard:
                        txObj.put("type_enum", "standard");
                        break;
                    default:
                        txObj.put("type_enum", "unknown");
                        known = false;
                        break;
                }
                txObj.put("timestamp", tx.getTimestamp());
                if (known) {
                    txObj.put("amount", tx.getAmount());
                    txObj.put("fee", tx.getFee());
                    txObj.put("sender", ByteUtil.arrayAsStringWithDashes(tx.getSenderIdentifier()));
                    txObj.put("receiver", ByteUtil.arrayAsStringWithDashes(tx.getReceiverIdentifier()));
                    txObj.put("sender_nyzo_string", NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(tx.getSenderIdentifier())));
                    txObj.put("receiver_nyzo_string", NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(tx.getReceiverIdentifier())));
                    txObj.put("id", ByteUtil.arrayAsStringWithDashes(HashUtil.doubleSHA256(tx.getBytes(true))));
                    if (tx.getSenderData() != null) {
                        txObj.put("sender_data", ByteUtil.arrayAsStringNoDashes(tx.getSenderData()));
                    } else {
                        txObj.put("sender_data", "");
                    }
                    txObj.put("previous_block_hash", ByteUtil.arrayAsStringNoDashes(tx.getPreviousBlockHash()));
                    if (tx.getSignature() != null) {
                        txObj.put("signature", ByteUtil.arrayAsStringNoDashes(tx.getSignature()));
                    } else {
                        txObj.put("signature", "");
                    }
                    txObj.put("previous_hash_height", tx.getPreviousHashHeight());
                }
                txs.add(txObj);
            }
            reply.put("transactions", txs);
            reply.put("hash", ByteUtil.arrayAsStringNoDashes(block.getHash()));
            reply.put("balance_list_hash", ByteUtil.arrayAsStringNoDashes(block.getBalanceListHash()));
            reply.put("previous_block_hash", ByteUtil.arrayAsStringNoDashes(block.getPreviousBlockHash()));
            return new JSONRPC2Response(reply, req.getID());
        }
    }
}

