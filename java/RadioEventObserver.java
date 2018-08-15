import java.util.Observable;
import java.util.Observer;
import org.contikios.cooja.Simulation;

import org.apache.log4j.Logger;

import UnrealCoojaMsg.Message;
import UnrealCoojaMsg.MsgType;
import UnrealCoojaMsg.RadioState;
import com.google.flatbuffers.*;

import org.contikios.cooja.ClassDescription;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.producer.Producer;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.MoteInterface;
/* Radio Event Observer
 *
 * A specialised event observer for a mote's radio interface events
 */
public class RadioEventObserver extends InterfaceEventObserver {

	public RadioEventObserver(Simulation sim, MoteObserver parent, Mote mote,
														Observable radioInterface,
														Producer<String, byte[]> kafkaProducer){
		super(sim, parent, mote, radioInterface, kafkaProducer);
	}

  @Override
	public void update(Observable radioObservable, Object obj) {
		Radio radio = (Radio) radioObservable;
		FlatBufferBuilder builder = new FlatBufferBuilder(50);
		Message.startMessage(builder);
		Message.addType(builder, MsgType.RADIO_STATE);
		Message.addId(builder, mote.getID() - 1);
		Message.addRadioState(builder, RadioState.createRadioState(builder, radio.isRadioOn(),
													radio.getCurrentSignalStrength(),
													radio.getCurrentOutputPower()));
		int msg = Message.endMessage(builder);
		Message.finishMessageBuffer(builder, msg);
		publish("radio", "" + mote.getID(), builder.sizedByteArray());
  }
}
