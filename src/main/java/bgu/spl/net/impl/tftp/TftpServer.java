package bgu.spl.net.impl.tftp;
import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {

        if(args.length == 1){
                Server.threadPerClient( //
                    Integer.parseInt(args[0]), 
                    () -> new TftpProtocol(), 
                    TftpEncoderDecoder::new 
                ).serve();
        }
        else {
            System.out.println("Invalid port number. Exiting...");
        }
    }
}