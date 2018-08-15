import org.apache.log4j.Logger;
import org.contikios.cooja.Simulation;
import java.util.Observable;

import org.contikios.cooja.ClassDescription;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.producer.Producer;

import org.contikios.cooja.Mote;
import org.contikios.cooja.mspmote.MspMote;
import se.sics.mspsim.core.OperatingModeListener;
import org.contikios.cooja.MoteInterface;
import se.sics.mspsim.core.Chip;
/* Radio Event Observer
 *
 * A specialised event observer for a mote's radio interface events
 */
public class CPUEventObserver extends InterfaceEventObserver implements OperatingModeListener{
  private MspMote mote;
  private MoteObserver parent;
  private static Logger logger = Logger.getLogger(InterfaceEventObserver.class);

  public CPUEventObserver(Mote mote,
														Producer<String, byte[]> kafkaProducer){
    super(mote, new Observable(), kafkaProducer);
    logger.info("Created CPU observer");
    this.mote = (MspMote)mote;
    this.mote.getCPU().addOperatingModeListener(this);
  }

  public void modeChanged(Chip source, int mode) {
    // parent.cpuEventHandler(mote.getCPU(), mote);
    // cpu.getMode(), MSP430Constants.MODE_NAMES[cpu.getMode()]
  }

  public void removeListener() {
    this.mote.getCPU().removeOperatingModeListener(this);
  }

}
