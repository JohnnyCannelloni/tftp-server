package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    static private ConcurrentHashMap<String, Boolean> loggedIn = new ConcurrentHashMap<String, Boolean>();
    static private ConcurrentHashMap<Integer, String> idToUsername = new ConcurrentHashMap<Integer, String>();

    private int connectionId;
    private Connections<byte[]> connections;
    private String username;
    private boolean isLoggedIn;
    private List<byte[]> dataPackets = new LinkedList<>();
    private String filenameToCreate;
    private String fileFolder = "Files";
    private byte[] file;

    private boolean shouldTerminate;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.isLoggedIn = false;
        this.shouldTerminate = false;
        this.filenameToCreate = "";
    }

    @Override
    public void process(byte[] message) {

        short opcode = (short) (((short) message[0]) << 8 | (short) (message[1]) & 0x00ff); 

        if (!isLoggedIn && opcode != 7) {
            connections.send(connectionId, createErrorPacket(6, "User not logged in")); 
        }

        else {
            switch(opcode){
                case 1:
                    // RRQ
                    handleRRQ(message); 
                    break;
                case 2:
                    // WRQ
                    handleWRQ(message); 
                    break;
                case 3:
                    // DATA
                    handleData(message); 
                    break;
                case 4:
                    // ACK
                    handleACK(message); 
                    break;
                case 5:
                    // ERROR
                    handleERROR(message);
                    break;
                case 6:
                    // DIRQ
                    handleDIRQ(message); 
                    break;
                case 7:
                    // LOGRQ
                    handleLOGRQ(message); 
                    break;
                case 8:
                    // DELRQ
                    handleDELRQ(message); 
                    break;
                case 9:
                    // BCAST
                    handleBCAST(message); 
                    break;
                case 10:
                    // DISC
                    handleDISC(); 
                    break;
            }
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    private void handleRRQ(byte[] message) {
        String filename = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); 
        File responseFile = new File(fileFolder + "/" + filename);
        System.out.println("RRQ Filename: " + filename);

        if(responseFile.exists()){
            try {
                file = Files.readAllBytes(responseFile.toPath());
                fileToData();

                 // send the first data packet to the client, and if there are more data packets to send, they will be sent in the processACK method.
                if(dataPackets.size() > 0){
                    byte[] dataPacket = dataPackets.remove(0);
                    connections.send(connectionId, dataPacket);
                }
            }
            catch (Exception e){
                e.printStackTrace();
                connections.send(connectionId, createErrorPacket(0, "Not defined, see error message (if any)"));
                return;
            }
        }
        else{
            connections.send(connectionId, createErrorPacket(1, "File not found"));
        }
    }

    private void handleWRQ(byte[] message) {

        //Check if file exists, if it does send an error packet
        String filename = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); 
        File requestedFile = new File(fileFolder + "/" + filename); 

        if (!requestedFile.exists()){
            file = new byte[0];
            filenameToCreate = filename;
            connections.send(connectionId, createACKPacket((short) 0));
        }
        else{
            connections.send(connectionId, createErrorPacket(5, "File already exists"));
        }
    }

    private void addData(byte[] dataPacket) {
        byte[] newFile = new byte[file.length + dataPacket.length];
        System.arraycopy(file, 0, newFile, 0, file.length);
        System.arraycopy(dataPacket, 0, newFile, file.length, dataPacket.length);
        file = newFile;
    }

    private void handleData(byte[] message) {
        short blockNumber = (short) (((short) message[4]) << 8 | (short) (message[5]) & 0x00ff); // decode the block number 
        byte[] data = new byte[message.length - 6];
        System.arraycopy(message, 6, data, 0, message.length - 6); 
        boolean isLastBlock = data.length < 512;
        addData(data);
       
        if(isLastBlock){
            // create the file from the dataPackets list and send the client an ACK
            Path filePath = Paths.get(fileFolder, filenameToCreate);

            try{
                Files.write(filePath, file);
            }
            catch (Exception e){
                e.printStackTrace();
                connections.send(connectionId, createErrorPacket(0, "Not defined, see error message (if any)"));
                filenameToCreate = "";
                return;
            }
            
            connections.send(connectionId, createACKPacket(blockNumber));
            handleBCAST(createBCASTPacket(filenameToCreate, true));
            filenameToCreate = "";
        }

        else{
            // send the client an ACK with the block number of the data packet
            connections.send(connectionId, createACKPacket((short) blockNumber));
        }
    }

    private void handleACK(byte[] message) {
        if(dataPackets.size() > 0){
            byte[] dataPacket = dataPackets.remove(0);
            connections.send(connectionId, dataPacket);
        }
        else{
            //the transfer of data packets is finished
            filenameToCreate = "";
        }
    }

    private void handleERROR(byte[] message) {
        return;
    }

    private void handleDIRQ(byte[] message) {

        File folder = new File(fileFolder);
        File[] files = folder.listFiles();
        ByteArrayOutputStream arrayStream = new ByteArrayOutputStream();

        for (File file : files) {
            if (file.isFile()) {
                try {
                    arrayStream.write((file.getName()+"\n").getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                arrayStream.write((byte)0); 
            }
        }

        byte[] filesInBytes = arrayStream.toByteArray();

        // we sent the data packets to the client in chunks of 512 bytes, and the last data packet is less than 512 bytes
        // create data packets from the files, and add them to the packets list

        int blockNum = 1;
        int bytesRead;
        byte[] buffer = new byte[512];
        int i = 0;
        while (i < filesInBytes.length) {
            bytesRead = Math.min(512, filesInBytes.length - i);
            System.arraycopy(filesInBytes, i, buffer, 0, bytesRead);
            byte[] dataPacket = createDataPacket(buffer, bytesRead, blockNum);
            dataPackets.add(dataPacket);
            blockNum++;
            i += bytesRead;
        }

        if(dataPackets.size() > 0){
            byte[] dataPacket = dataPackets.remove(0);
            connections.send(connectionId, dataPacket);
        } 
    }

    private void handleLOGRQ(byte[] message) { 

        if (isLoggedIn) {
            connections.send(connectionId, createErrorPacket(7, "User already logged in"));
        }

        else {
            username = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); 

            if(loggedIn.get(username) == null || loggedIn.get(username) == false){ 
                loggedIn.put(username, true); 
                idToUsername.put(connectionId, username);
                isLoggedIn = true;
                connections.send(connectionId, createACKPacket((short)0));
            }
            else{ 
                connections.send(connectionId, createErrorPacket(7, "User already logged in"));
            }
        }
    }

    private void handleDELRQ(byte[] message) {
        String filename = new String(message, 2, message.length - 3, StandardCharsets.UTF_8); 
        System.out.println("Filename: " + filename);
        File file = new File(fileFolder + "/" + filename);

        if (file.exists()){
            file.delete(); 
            connections.send(connectionId, createACKPacket((short)0));
            handleBCAST(createBCASTPacket(filename, false));
        }

        else{
            connections.send(connectionId, createErrorPacket(1, "Filename not found"));
        }
    }

    private void handleBCAST(byte[] message) {
        for (Integer conId : idToUsername.keySet()) {
            connections.send(conId, message);
        }
    }

    private void handleDISC() {
        connections.send(connectionId, createACKPacket((short) 0));
        loggedIn.put(username, false);
        idToUsername.remove(connectionId);
        isLoggedIn = false;
        shouldTerminate = true;
    }

    private byte[] createACKPacket(short blockNumber) {
        byte[] ackPacket = new byte[4];
        ackPacket[0] = 0; 
        ackPacket[1] = 4; 
        ackPacket[2] = (byte) (blockNumber >> 8); // First byte of the block number
        ackPacket[3] = (byte) (blockNumber & 0xFF); // Second byte of the block number
        return ackPacket;
    }

    private byte[] createDataPacket(byte[] buffer, int bytesRead, int blockNumber) {
        byte[] dataPacket = new byte[bytesRead + 6];
        short packetSize = (short) bytesRead;
        dataPacket[0] = 0;
        dataPacket[1] = 3; 
        dataPacket[2] = (byte) (packetSize >> 8);
        dataPacket[3] = (byte) (packetSize & 0xFF);
        dataPacket[4] = (byte) (blockNumber >> 8);
        dataPacket[5] = (byte) (blockNumber & 0xFF);
        System.arraycopy(buffer, 0, dataPacket, 6, bytesRead);

        return dataPacket;
    }

    private byte[] createErrorPacket(int errorCode, String errMsg) { 
        byte[] errMsgBytes = errMsg.getBytes();
        byte[] errorPacket = new byte[errMsgBytes.length + 5];
        errorPacket[0] = 0;
        errorPacket[1] = 5; // opcode for ERROR
        errorPacket[2] = 0; // error code
        errorPacket[3] = (byte) errorCode;
        System.arraycopy(errMsgBytes, 0, errorPacket, 4, errMsgBytes.length);
        errorPacket[errMsgBytes.length + 4] = 0;
        return errorPacket;
    }

    private byte[] createBCASTPacket(String fileName, boolean isAdded) { 
        byte[] bcastPacket = new byte[fileName.length() + 4];
        bcastPacket[0] = 0;
        bcastPacket[1] = 9; 
        bcastPacket[2] = (byte) (isAdded ? 1 : 0);
        System.arraycopy(fileName.getBytes(), 0, bcastPacket, 3, fileName.length());
        bcastPacket[fileName.length() + 3] = 0;

        return bcastPacket;
    }

    
    private void fileToData() {
        dataPackets.clear();
        int blockNum = 1;
        int bytesRead;
        byte[] buffer = new byte[512];
        int i = 0;
        while (i < file.length) {
            bytesRead = Math.min(512, file.length - i);
            System.arraycopy(file, i, buffer, 0, bytesRead);
            byte[] dataPacket = createDataPacket(buffer, bytesRead, blockNum);
            dataPackets.add(dataPacket);
            blockNum++;
            i += bytesRead;
        }
    }
}
