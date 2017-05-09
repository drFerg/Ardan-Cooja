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
	int port;
	DatagramPacket sendPacket;
	byte[] data;

	public RadioMediumEventObserver(CoojaEventObserver parent, RadioMedium network, InetAddress clientIPAddr, int clientPort){
		this.network = network;
		this.parent = parent;
		this.port = clientPort;
		try {
			clientSocket = new DatagramSocket();
			ipAddress = clientIPAddr;
		} catch (Exception e) {
			logger.error("RMEO>> " + e.getMessage());
		}
		this.network.addRadioTransmissionObserver(this);
			logger.info("Created radio medium observer");
	}

	@Override
	public void update(Observable obs, Object obj) {
//		parent.radioMediumEventHandler(network.getLastConnection());
		RadioConnection conn = network.getLastConnection();
		if (conn == null) return;
		//logger.info(conn);
		Radio[] dests = conn.getDestinations();
		if (dests.length == 0) return;
		data = new byte[3 + dests.length];
		data[0] = NETWORK_PKT;
		data[1] = (byte) (conn.getSource().getMote().getID() - 1);
		data[2] = (byte) dests.length;
		for (int i = 0; i < dests.length; i++) {
			data[3 + i] = (byte) (dests[i].getMote().getID() - 1);
		}
		try {
			sendPacket = new DatagramPacket(data, data.length, ipAddress, port);
			clientSocket.send(sendPacket);
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
	}

	public void deleteObserver() {
		network.deleteRadioTransmissionObserver(this);
	}
}
