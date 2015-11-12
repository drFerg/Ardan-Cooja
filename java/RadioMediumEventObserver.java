import java.util.Observable;
import java.util.Observer;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;

import org.contikios.cooja.RadioMedium;

/* Radio Event Observer
 * 
 * A specialised event observer for a mote's radio interface events 
 */
@ClassDescription("Radio Medium Event Observer")
public class RadioMediumEventObserver implements Observer {
	private RadioMedium network;
	protected CoojaEventObserver parent;
	protected static Logger logger = Logger.getLogger(InterfaceEventObserver.class);

	public RadioMediumEventObserver(CoojaEventObserver parent, RadioMedium network){
		this.network = network;
	this.parent = parent;
	this.network.addRadioTransmissionObserver(this);
		logger.info("Created radio medium observer");
	}

	@Override
	public void update(Observable obs, Object obj) {
	parent.radioMediumEventHandler(network.getLastConnection());
	}

	public void deleteObserver() {
		network.deleteRadioTransmissionObserver(this);
	}
}