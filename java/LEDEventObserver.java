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

    public LEDEventObserver(MoteObserver parent, Mote mote,
                            Observable interfaceToObserve) {
        super(parent, mote, interfaceToObserve);
        this.leds = (LED) interfaceToObserve;
        logger.info("Created LED observer");
    }

    @Override
    public void update(Observable observable, Object o) {
        byte[] status = new byte[4];
        status[1] = (byte) (leds.isRedOn()? 1: 0);
        status[2] = (byte) (leds.isGreenOn()? 1: 0);
        status[3] = (byte) (leds.isYellowOn()? 1: 0);
        try {
            DatagramSocket clientSocket = new DatagramSocket();
            InetAddress IPAddress = InetAddress.getByName("localhost");
            status[0] = (byte) (mote.getID() - 1);
            DatagramPacket sendPacket = new DatagramPacket(status, status.length, IPAddress, 5000);
            clientSocket.send(sendPacket);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }
}
