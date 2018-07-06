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

import java.util.Properties;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
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
    Producer<String, byte[]> kafka;

    public LEDEventObserver(MoteObserver parent, Mote mote,
                            Observable interfaceToObserve,
                            InetAddress clientIPAddr, int clientPort, Producer<String, byte[]> p) {

        super(parent, mote, interfaceToObserve);
        this.leds = (LED) interfaceToObserve;
        this.port = clientPort;
        kafka = p;
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

        FlatBufferBuilder builder = new FlatBufferBuilder(50);
        int ledVec = Message.createLedVector(builder, status);
        Message.startMessage(builder);
    		Message.addType(builder, MsgType.LED);
    		Message.addId(builder, mote.getID() - 1);
    		Message.addLed(builder, ledVec);
    		int msg = Message.endMessage(builder);
        Message.finishMessageBuffer(builder, msg);


        try {
            // sendPacket = new DatagramPacket(data, data.length, ipAddress, port);
            // clientSocket.send(sendPacket);
            kafka.send(new ProducerRecord<String, byte[]>("actuator", "", builder.sizedByteArray()));
            logger.info("Sending led info to kafka stream");
        } catch (Exception e) {
            logger.info("Exception:" + e.getMessage());
        }
    }
}
