import org.contikios.cooja.Mote;
import org.apache.log4j.Logger;
import org.contikios.cooja.interfaces.LED;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Observable;
import java.util.Observer;

import UnrealCoojaMsg.Message;
import UnrealCoojaMsg.MsgType;
import com.google.flatbuffers.*;
/**
 * Created by fergus on 09/11/15.
 */
public class LEDEventObserver extends InterfaceEventObserver {
    private static Logger logger = Logger.getLogger(InterfaceEventObserver.class);
    private LED leds;
    private boolean red;
    private boolean yellow;
    private boolean green;
    DatagramSocket clientSocket;
    InetAddress ipAddress;
    DatagramPacket sendPacket;
    int[] status;
    int port;

    public LEDEventObserver(MoteObserver parent, Mote mote,
                            Observable interfaceToObserve,
                            InetAddress clientIPAddr, int clientPort) {

        super(parent, mote, interfaceToObserve);
        this.leds = (LED) interfaceToObserve;
        this.port = clientPort;
        logger.info("Created LED observer");
        try {
            clientSocket = new DatagramSocket();
            ipAddress = clientIPAddr;
        } catch (Exception e) {
            logger.error("LEDEO>> " + e.getMessage());
        }
        status = new int[3];
    }

    @Override
    public void update(Observable observable, Object o) {
        status[0] = (leds.isRedOn()? 1: 0);
        status[1] = (leds.isGreenOn()? 1: 0);
        status[2] = (leds.isYellowOn()? 1: 0);

        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int ledVec = Message.createLedVector(builder, status);
        Message.startMessage(builder);
    		Message.addType(builder, MsgType.LED);
    		Message.addId(builder, mote.getID() - 1);
    		Message.addLed(builder, ledVec);
    		int msg = Message.endMessage(builder);
        Message.finishMessageBuffer(builder, msg);
        byte[] data = builder.sizedByteArray();

        try {
            sendPacket = new DatagramPacket(data, data.length, ipAddress, port);
            clientSocket.send(sendPacket);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }
}
