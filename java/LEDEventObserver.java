import org.contikios.cooja.Mote;
import org.apache.log4j.Logger;
import org.contikios.cooja.interfaces.LED;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by fergus on 09/11/15.
 */
public class LEDEventObserver extends InterfaceEventObserver {
    private static Logger logger = Logger.getLogger(InterfaceEventObserver.class);
    private LED leds;
    private boolean red;
    private boolean yellow;
    private boolean green;
    DatagramSocket clientSocket;
    InetAddress ipAddress;
    DatagramPacket sendPacket;
    byte[] status;

    public LEDEventObserver(MoteObserver parent, Mote mote,
                            Observable interfaceToObserve) {
        super(parent, mote, interfaceToObserve);
        this.leds = (LED) interfaceToObserve;
        logger.info("Created LED observer");
        try {
            clientSocket = new DatagramSocket();
            ipAddress = InetAddress.getByName("localhost");
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    @Override
    public void update(Observable observable, Object o) {
        status = new byte[5];
        status[2] = (byte) (leds.isRedOn()? 1: 0);
        status[3] = (byte) (leds.isGreenOn()? 1: 0);
        status[4] = (byte) (leds.isYellowOn()? 1: 0);
        try {
            status[1] = (byte) (mote.getID() - 1);
            sendPacket= new DatagramPacket(status, status.length, ipAddress, 5000);
            clientSocket.send(sendPacket);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }
}