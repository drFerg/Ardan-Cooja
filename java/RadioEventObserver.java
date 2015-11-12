import java.util.Observable;
import java.util.Observer;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.MoteInterface;
/* Radio Event Observer
 * 
 * A specialised event observer for a mote's radio interface events 
 */
public class RadioEventObserver extends InterfaceEventObserver {

	public RadioEventObserver(MoteObserver parent, Mote mote, 
                            Observable interfaceToObserve){
		super(parent, mote, interfaceToObserve);
		logger.info("Created radio observer");
    parent.radioEventHandler((Radio) interfaceToObserve, mote);
	}

  @Override
	public void update(Observable radio, Object obj) {
    parent.radioEventHandler((Radio) radio, mote);
  }
}