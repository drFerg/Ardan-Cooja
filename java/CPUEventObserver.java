import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;

import org.contikios.cooja.Mote;
import org.contikios.cooja.mspmote.MspMote;
import se.sics.mspsim.core.OperatingModeListener;
import org.contikios.cooja.MoteInterface;
import se.sics.mspsim.core.Chip;
/* Radio Event Observer
 * 
 * A specialised event observer for a mote's radio interface events 
 */
public class CPUEventObserver implements OperatingModeListener{
  private MspMote mote;
  private MoteObserver parent;
  private static Logger logger = Logger.getLogger(InterfaceEventObserver.class);

  public CPUEventObserver(MoteObserver parent, Mote mote){
    logger.info("Created CPU observer");
    this.parent = parent;
    this.mote = (MspMote)mote;
    this.mote.getCPU().addOperatingModeListener(this);
    parent.cpuEventHandler(this.mote.getCPU(), mote);
  }

  public void modeChanged(Chip source, int mode) {
    parent.cpuEventHandler(mote.getCPU(), mote);
  }

  public void removeListener() {
    this.mote.getCPU().removeOperatingModeListener(this);
  }

}