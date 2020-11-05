//import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;

import java.util.HashMap;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    private Packet []windowsA;
    private HashMap<Integer, Packet> bufferA;
    private HashMap<Integer, Packet> bufferB;
    private Packet []windowsB;

    private int curr_seq;
    private int send_base;

    private int rcv_curr;
    private int rcv_base;
    private int currentExpectedACK;
    private int currentExpectedSEQ;
    
    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
	    WindowSize = winsize;
	    LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
	    RxmtInterval = delay;
    }

    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
        while(curr_seq - send_base + 1 < windowsA.length){
            int seqNo = curr_seq+1 % LimitSeqNo;
            int ackNo = -1;
            int checksum = makeCheckSum(message.getData());
            String payload = message.getData();

            Packet packet = new Packet(seqNo, ackNo, checksum, payload);
            toLayer3(A, packet);
            messageNum++;
            start_time = getTime();
            bufferA.put(seqNo, packet);
            startTimer(A, RxmtInterval);
            curr_seq++;
        }
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        end_time = getTime();
        rtt += (end_time - start_time);
        stopTimer(A);
        int checksum = packet.getChecksum();
        int ackNo = packet.getAcknum();

        // not corrupted
        if (checkSum(checksum, packet.getPayload())){
            // mark packet as ACKed
            bufferA.get(ackNo).setAcknum(ackNo);
            // remove all leading ACKed packet from buffer
            while(bufferA.containsKey(send_base)){
                if (bufferA.get(send_base).getAcknum() == -1){
                    break;
                }
                bufferA.remove(send_base);
                send_base++;
                send_base%=LimitSeqNo;
            }
        }else{ //corrupted, send first unacknowledged packet
            toLayer3(A, bufferA.get(send_base));
            retransmitNum++;
            corruptedNum++;
            startTimer(A, RxmtInterval);
        }
        //duplicated packet do nothing
    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
        stopTimer(A);
        Packet retransmitPacket = bufferA.get(send_base); //retransmit the first unacknowledged packet
        toLayer3(A, retransmitPacket);
        retransmitNum++;
        lostNum++;
        startTimer(A, RxmtInterval);
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        windowsA = new Packet[WindowSize];
        send_base = 0;
        curr_seq = FirstSeqNo - 1;
        bufferA = new HashMap<>();
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        delivered++;
        int seqNo = packet.getSeqnum();
        int ackNo = seqNo;
        int checksum = packet.getChecksum();
        String payload = packet.getPayload();
        if(checkSum(checksum, packet.getPayload()) && (duplicatedSeq(packet.getSeqnum(), bufferB))){ //duplicated packet, return packet as response
            Packet response = new Packet(seqNo,ackNo,checksum, payload);
            toLayer3(B,response);
            ACK_B++;
        }else if (checkSum(checksum, packet.getPayload())){ // not corrupted
            Packet response = new Packet(seqNo,ackNo,checksum, payload);
            toLayer3(B,response);
            bufferB.put(seqNo, packet);
            while(bufferB.containsKey(rcv_base)){
                toLayer5(bufferB.get(rcv_base).getPayload());
                bufferB.remove(rcv_base);
                rcv_base++;
                rcv_base %= LimitSeqNo;
            }
        }else{ // corrupted
            // do nothing
            corruptedNum++;
        }
    }

    private boolean duplicatedSeq(int seqnum, HashMap<Integer,Packet> bufferB) {
        int minSeq = LimitSeqNo+1;
        if (bufferB.containsKey(seqnum)){
            return true;
        }
        for (Integer Key: bufferB.keySet()){
            minSeq = Math.min(minSeq, Key);
        }
        if (minSeq > seqnum){
            return true;
        }else{
            return false;
        }
    }

    private boolean checkSum(int result, String payload) {
        byte[] bytes = payload.getBytes();
        Checksum crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return ((int) crc32.getValue()) == result;
    }

    private int makeCheckSum(String payload) {
        byte[] bytes = payload.getBytes();
        Checksum crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return ((int) crc32.getValue());
    }
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {
        windowsB = new Packet[WindowSize];
        rcv_base = 0;
        rcv_curr = FirstSeqNo - 1;
        bufferB = new HashMap<>();
    }

    private int messageNum = 0;
    private int retransmitNum = 0;
    private int delivered = 0;
    private int ACK_B = 0;
    private int corruptedNum = 0;
    private int lostNum = 0;
    private double rtt = 0;
    private double start_time;
    private double end_time;
    // Use to print final statistics
    protected void Simulation_done()
    {
    	// TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
    	System.out.println("\n\n===============STATISTICS=======================");
    	System.out.println("Number of original packets transmitted by A:" + messageNum);
    	System.out.println("Number of retransmissions by A:" + retransmitNum);
    	System.out.println("Number of data packets delivered to layer 5 at B:" + delivered);
    	System.out.println("Number of ACK packets sent by B:" + ACK_B);
    	System.out.println("Number of corrupted packets:" + corruptedNum);
    	System.out.println("Ratio of lost packets:" + lostNum);
    	System.out.println("Ratio of corrupted packets:" + corruptedNum);
    	System.out.println("Average RTT:" + rtt/(messageNum - corruptedNum - lostNum));
    	System.out.println("Average communication time:" + "<YourVariableHere>");
    	System.out.println("==================================================");

    	// PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
    	System.out.println("\nEXTRA:");
    	// EXAMPLE GIVEN BELOW
    	//System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }	

}
