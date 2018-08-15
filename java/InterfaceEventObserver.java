import java.util.Observable;
import java.util.Observer;
import org.contikios.cooja.Simulation;

import org.apache.log4j.Logger;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.producer.Producer;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
/* Interface Event Observer
 *
 * A generic event observer for a mote interface
 */
@ClassDescription("Interface Event Observer")
public class InterfaceEventObserver implements Observer {
    protected Simulation sim;
    protected Mote mote = null;
    private Observable interfaceObservable;
    protected MoteObserver parent;
    protected Producer<String, byte[]> kafka;
    protected static Logger logger = Logger.getLogger(InterfaceEventObserver.class);

    public InterfaceEventObserver(Simulation sim, MoteObserver parent, Mote mote,
        Observable interfaceToObserve, Producer<String, byte[]> kafka) {
      interfaceObservable = interfaceToObserve;
      this.sim = sim;
      this.parent = parent;
      this.mote = mote;
      this.kafka = kafka;
      interfaceObservable.addObserver(this);
    }

    public Observable getInterfaceObservable(){
      return interfaceObservable;
    }

    public boolean publish(String topic, String key, byte[] value){
      try {
          kafka.send(new ProducerRecord<String, byte[]>(topic, key, value));
          logger.info("SEND: " + sim.getSimulationTime());
          return true;
      } catch (Exception e) {
          logger.info("Exception:" + e.getMessage());
          return false;
      }
    }

    public void update(Observable obs, Object obj) {
      final MoteInterface moteInterface = (MoteInterface) obs;
      int moteID = mote.getID();

      logger.info("'" + Cooja.getDescriptionOf(moteInterface.getClass())
          + "'" + " of mote '" + (moteID > 0 ? Integer.toString(moteID) : "?")
          + "'");
    }
  }
