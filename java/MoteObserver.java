import java.util.ArrayList;
import java.net.InetAddress;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.producer.Producer;
import org.contikios.cooja.Simulation;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.log4j.Logger;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.interfaces.Beeper;

import java.util.Observer;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.interfaces.Radio;
import se.sics.mspsim.core.MSP430;

/* MoteObserver
 *
 * Contains all the interface observers for each interface within a mote
 */
@ClassDescription("Mote Observer")
public class MoteObserver {
    protected Mote mote = null;
    protected CPUEventObserver cpu;
    protected ArrayList<InterfaceEventObserver> observers;
    protected LED leds;
    private static Logger logger = Logger.getLogger(MoteObserver.class);
    Producer<String, byte[]> kafka;
    Simulation sim;

    public MoteObserver(Simulation sim, Mote moteToObserve, Producer<String, byte[]> producer) {
      this.mote = moteToObserve;
      kafka = producer;
      this.sim = sim;
      observers = new ArrayList<InterfaceEventObserver>();
      observeMoteInterfaces();
    }

    public void observeMoteInterfaces(){
      logger.info("Adding interfaces for mote: " + mote.getID());
      //cpu = new CPUEventObserver(this, mote);
      //   observers.add(new InterfaceEventObserver(this, mote, mi));
        observers.add(new RadioEventObserver(mote, mote.getInterfaces().getRadio(), kafka));
        observers.add(new LEDEventObserver(mote, mote.getInterfaces().getLED(), kafka));
        observers.add(new BeeperEventObserver(mote, mote.getInterfaces().getBeeper(), kafka));
      }
    }

    public void deleteAllObservers(){
      logger.info("Removing interfaces for mote: " + mote.getID());
      for (InterfaceEventObserver moteObserver : observers) {
        moteObserver.getInterfaceObservable().deleteObserver(moteObserver);
      }
      //cpu.removeListener();
    }
    // public void radioEventHandler(Radio radio, Mote mote){
    //   parent.radioEventHandler(radio, mote);
    // }
    //
    // public void cpuEventHandler(MSP430 cpu, Mote mote){
    //   parent.cpuEventHandler(cpu, mote);
    // }
  }
