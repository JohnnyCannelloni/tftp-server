package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private byte[] bytes = new byte[1 << 10]; // number of bytes can be at most 1024 (2^10)
    private int len = 0;
    private short opcode = -1;
    private short dataSize = -1;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        bytes[len] = nextByte;
        len++;

        if(len == 2){
            opcode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0x00ff);
        }
        if(len >= 2){
            return handle();
        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message) { 
        return message;
    }

    private byte[] handle(){
        switch(opcode){
            case 1:
                // RRQ
                if (bytes[len-1] == 0) 
                    return popBytes();
                else
                    return null;
            case 2:
                // WRQ
                if (bytes[len-1] == 0) 
                    return popBytes();
                else
                    return null;
            case 3:
                // DATA
                if (len == 4) 
                    dataSize = (short) (((short) bytes[2]) << 8 | (short) (bytes[3]) & 0x00ff); // decode the packet size
                else if (len == (6 + dataSize)) 
                    return popBytes();
                else
                    return null;
            case 4:
                // ACK
                if (len == 4) 
                    return popBytes();
                else
                    return null;
            case 5:
                // ERROR
                if(len > 4 && bytes[len-1] == 0)
                    return popBytes();
                else
                    return null;
            case 6:
                // DIRQ
                if (len == 2) 
                    return popBytes();
                else
                    return null;
            case 7:
                // LOGRQ
                if (bytes[len-1] == 0) 
                    return popBytes();
                else
                    return null;
            case 8:
                // DELRQ
                if (bytes[len-1] == 0) 
                    return popBytes();
                else
                    return null;
            case 9:
                // BCAST
                if (len > 3 && bytes[len-1] == 0) 
                    return popBytes();
                else
                    return null;
            case 10:
                // DISC
                if (len == 2) 
                    return popBytes();
                else 
                    return null;
        }
        return null;
    }

    private byte[] popBytes() {
        byte[] bytesArr = new byte[len];
        System.arraycopy(bytes, 0, bytesArr, 0, len);

        // reset the bytes array and the len 
        opcode = -1;
        dataSize = -1;
        len = 0;

        return bytesArr;
    }
}