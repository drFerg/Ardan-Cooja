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
    int port;

    public LEDEventObserver(MoteObserver parent, Mote mote,
                            Observable interfaceToObserve,
                            InetAddress clientIPAddr, int clientPort) {

        super(parent, mote, interfaceToObserve);
        this.leds = (LED) interfaceToObserve;
        this.port = clientPort;
        logger.info("Created LED observer");
        try {
            clientSocket = new DatagramSocket();
            ipAddress = clientIPAddr;
        } catch (Exception e) {
            logger.error("LEDEO>> " + e.getMessage());
        }
        status = new byte[5];
        status[0] = (byte) 0;
    }

    @Override
    public void update(Observable observable, Object o) {
        status[1] = (byte) (mote.getID() - 1);
        status[2] = (byte) (leds.isRedOn()? 1: 0);
        status[3] = (byte) (leds.isGreenOn()? 1: 0);
        status[4] = (byte) (leds.isYellowOn()? 1: 0);
        try {
            sendPacket = new DatagramPacket(status, status.length, ipAddress, port);
            clientSocket.send(sendPacket);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }
}
