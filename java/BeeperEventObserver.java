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
public class BeeperEventObserver extends InterfaceEventObserver {
    public BeeperEventObserver(Mote mote,
                            Observable ledInterface,
                            Producer<String, byte[]> kafkaProducer) {
        super(mote, ledInterface, kafkaProducer);
    }

    @Override
    public void update(Observable observable, Object o) {
        FlatBufferBuilder builder = new FlatBufferBuilder(50);
        Message.startMessage(builder);
    		Message.addType(builder, MsgType.BEEPER);
    		Message.addId(builder, mote.getID() - 1);
    		int msg = Message.endMessage(builder);
        builder.finish(msg);

        publish("actuator", "Beeper: " + mote.getID(), builder.sizedByteArray());
      }
}
