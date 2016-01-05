import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Observable;
import java.util.Observer;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;

import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.interfaces.Radio;

/* Radio Event Observer
 * 
 * A specialised event observer for a mote's radio interface events 
 */
@ClassDescription("Radio Medium Event Observer")
public class RadioMediumEventObserver implements Observer {
	private byte NETWORK_PKT = 1;
	private RadioMedium network;
	protected CoojaEventObserver parent;
	protected static Logger logger = Logger.getLogger(InterfaceEventObserver.class);
	DatagramSocket clientSocket;
	InetAddress ipAddress;
	DatagramPacket sendPacket;
	byte[] data;

	public RadioMediumEventObserver(CoojaEventObserver parent, RadioMedium network){
		this.network = network;
		this.parent = parent;
		try {
			clientSocket = new DatagramSocket();
			ipAddress = InetAddress.getByName("localhost");
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		data[0] = NETWORK_PKT;
		this.network.addRadioTransmissionObserver(this);
			logger.info("Created radio medium observer");
	}

	@Override
	public void update(Observable obs, Object obj) {
//		parent.radioMediumEventHandler(network.getLastConnection());
		RadioConnection conn = network.getLastConnection();
		Radio[] dests = conn.getDestinations();
		data = new byte[3 + dests.length];
		data[1] = (byte) (conn.getSource().getMote().getID() - 1);
		data[2] = (byte) dests.length;
		for (int i = 0; i < dests.length; i++) {
			data[3 + i] = (byte) (dests[i].getMote().getID() - 1);
		}
		try {
			sendPacket= new DatagramPacket(data, data.length, ipAddress, 5000);
			clientSocket.send(sendPacket);
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
	}

	public void deleteObserver() {
		network.deleteRadioTransmissionObserver(this);
	}
}