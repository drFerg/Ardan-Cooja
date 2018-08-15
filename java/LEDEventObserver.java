import org.contikios.cooja.Mote;
import org.apache.log4j.Logger;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.LED;

import java.util.Observable;
import java.util.Observer;

import UnrealCoojaMsg.Message;
import UnrealCoojaMsg.MsgType;
import com.google.flatbuffers.*;

import java.util.Properties;
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
    private LED leds;
    private int[] status;
    public LEDEventObserver(Simulation sim, MoteObserver parent, Mote mote,
                            Observable ledInterface,
                            Producer<String, byte[]> kafkaProducer) {
        super(sim, parent, mote, ledInterface, kafkaProducer);
        this.leds = (LED) ledInterface;
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
        builder.finish(msg);

        publish("actuator", "" + mote.getID(), builder.sizedByteArray());
      }
}
