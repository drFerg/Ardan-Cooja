import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.google.flatbuffers.*;
import UnrealCoojaMsg.Message;
import UnrealCoojaMsg.MsgType;
import org.apache.log4j.Logger;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.SimEventCentral.MoteCountListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.mspmote.SkyMote;
import org.contikios.cooja.mspmote.interfaces.SkyTemperature;
import se.sics.mspsim.core.MSP430;

/**
 *
 * This project must be loaded in COOJA before the plugin can be used:
 * Menu>Settings>Manage project directories>Browse>..>OK
 *
 * @author Fergus Leahy
 */

/*
 * Cooja Unreal plugin
 *
 * Pipes interface events (radio, CPU) from motes in Cooja into HWDB for analysis using automata.
 *
 */
@ClassDescription("Unreal Cooja") /* Description shown in menu */
@PluginType(PluginType.SIM_PLUGIN)
public class UnrealCooja extends VisPlugin implements CoojaEventObserver{
  private static final long serialVersionUID = 4368807123350830772L;
  private static Logger logger = Logger.getLogger(UnrealCooja.class);
  private final Thread udpHandler;
  private DatagramSocket udpSocket;
  private int PORT = 5011;
  private Simulation sim;
  private RadioMedium radioMedium;
  private RadioMediumEventObserver networkObserver;
  private MoteCountListener moteCountListener;
  private ArrayList<MoteObserver> moteObservers;
  private boolean initialised = false;
  private Cooja gui;

  private ArrayList<String> insertBuffer;
  private long lastTime;
  private long delay = 100;
  private int count = 0;
  private long connections = 0;
  private boolean mesh = true;
  /**
   * @param simulation Simulation object
   * @param gui GUI object
   */
  public UnrealCooja(Simulation simulation, Cooja gui) {
    super("Unreal Cooja", gui, false);
    sim = simulation;
    radioMedium = sim.getRadioMedium();
    this.gui = gui;


    /* Initialise Observers button */
    JButton button = new JButton("Observe");
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!initialised) initObservers();
      }
    });
    this.getContentPane().add(BorderLayout.NORTH, button);
    setSize(300,100);
    insertBuffer = new ArrayList<String>();
    try {
      udpSocket = new DatagramSocket(null);
      udpSocket.bind(new InetSocketAddress(PORT));
      logger.info("Listening on port " + PORT);
    } catch (IOException e){
      logger.info("Couldn't open socket on port " + PORT);
      logger.error(e.getMessage());
    }
    udpHandler = new IncomingDataHandler();
    udpHandler.start();
  }

  public void initObservers() {
    /* Check for class loaders, if not the same class casting won't work, reload to fix */
    if ((sim.getMotes()[0].getClass().getClassLoader() != this.getClass().getClassLoader()) &&
        (sim.getMotes()[0].getClass().getClassLoader() != gui.getClass().getClassLoader())) {
          logger.info("Different class loaders - Reload to fix");
          return;
    }
    initialised = true;
    networkObserver = new RadioMediumEventObserver(this, radioMedium);
    /* Create observers for each mote */
    moteObservers = new ArrayList<MoteObserver>();
    for(Mote mote : sim.getMotes()) {
      addMote(mote);
    }

    /* Listens for any new nodes added during runtime */
    sim.getEventCentral().addMoteCountListener(moteCountListener = new MoteCountListener() {
      public void moteWasAdded(Mote mote) {
        /* Add mote's radio to observe list */
        addMote(mote);
        logger.info("Added a mote");
      }
      public void moteWasRemoved(Mote mote) {
        /* Remove motes radio from observe list */
        logger.info("Removed a mote");
      }
    });
  }

  /* Adds a new mote to the observed set of motes
   * Needed for use in listener, to access /this/ context */
  public void addMote(Mote mote){
    moteObservers.add(new MoteObserver(this, mote));
  }

  public void closePlugin() {
    /* Clean up plugin resources */
    logger.info("Closing Network Socket...");
    udpHandler.interrupt();
    try {
      udpHandler.join();
    } catch (InterruptedException e) {
      logger.info("Interrupted whilst waiting for udpHandler");
      logger.error(e.getMessage());
    }
    logger.info("Tidying up UnrealCooja listeners/observers");
    if (!initialised) return;
    networkObserver.deleteObserver();
    sim.getEventCentral().removeMoteCountListener(moteCountListener);
    for(MoteObserver mote : moteObservers) {
      mote.deleteAllObservers();
    }
    logger.info("UnrealCooja cleaned up");
  }

  public void radioEventHandler(Radio radio, Mote mote) {
//    hwdb.insertLater(String.format("insert into radio values ('%d', '%d',\"%s\", '%d', '%1.1f', '%1.1f')\n",
//      sim.getSimulationTime(), mote.getID(), radio.getLastEvent(), (radio.isRadioOn() ? 1 : 0),
//      radio.getCurrentSignalStrength(), radio.getCurrentOutputPower()));
  }

  public void cpuEventHandler(MSP430 cpu, Mote mote){
//    hwdb.insertLater(String.format("insert into cpu values ('%d', '%d', '%d', \"%s\")\n", sim.getSimulationTime(), mote.getID(),
//                 cpu.getMode(), MSP430Constants.MODE_NAMES[cpu.getMode()]));
  }

  public void radioMediumEventHandler(RadioConnection conn) {
    if (conn == null) return;
    /* Retrieve connection data for transmission, including packet sequence number (made positive) */
    /* 6198FCCD AB000300 04920003 00040048 656C6C6F  a..............HelloDD10 < Unicast to 3 from 4
       4198F4CD ABFFFF00 06810006 0048656C 6C6F006E  A............Hello.nB0   < Broadcast from 6
                  ^^^^^^ ^^< address(to|from) */

//    byte[] pkt = conn.getSource().getLastPacketTransmitted().getPacketData();
//    hwdb.insertLater(String.format("insert into transmissions values ('%d', '%d', '%d', '%d', '%d', '%d', '%d', '%s' )\n",
//                                    pkt[2] & (0xff), /* Packet sequence number, made unsigned */
//                                    conn.getStartTime(), sim.getSimulationTime(),
//                                    conn.getSource().getMote().getID(), conn.getDestinations().length,
//                                    conn.getInterfered().length, pkt.length,
//                                    pkt[0] == 0x02 ? "false" : (pkt[5] == -1 && pkt[6] == -1 ? "true":"false")));
//                                    /* Check if packet is reply (<5 bytes), then check if it's a broadcast packet */
//    for (Radio dst: conn.getAllDestinations()) {
//      hwdb.insertLater(String.format("insert into connections values ('%d', '%d', '%d', '%d', '%d', '%s', '%d')\n",
//                                      conn.getSource().getLastPacketTransmitted().getPacketData()[2] & (0xff), conn.getStartTime(), sim.getSimulationTime(),
//                                      conn.getSource().getMote().getID(), dst.getMote().getID(),
//                                      (dst.isInterfered() ? "true" : "false"),
//                                      conn.getSource().getLastPacketTransmitted().getPacketData().length));
//      if (mesh && pkt[0] != 0x02 && (pkt[5] << 8 | pkt[6] &(0xff)) == dst.getMote().getID()) {
//        hwdb.insertLater(String.format("insert into meshLinks values ('%d', '%d', '%d', '%d', '%d', '%d', '%d')\n",
//                                      conn.getStartTime(), sim.getSimulationTime(),
//                                      conn.getSource().getMote().getID(), dst.getMote().getID(),
//                                      (pkt[10] << 8 | pkt[11] &(0xff)), (pkt[12] << 8 | pkt[13] &(0xff)),
//                                      conn.getSource().getLastPacketTransmitted().getPacketData().length));}
//    }
    connections++;
  }


  /* Forward data: Unreal Engine Mote -> Cooja mote */
  private class IncomingDataHandler extends Thread {
    byte[] data = new byte[200];
    DatagramPacket pkt = new DatagramPacket(data, 200);
    Runnable toRun = null;
    ByteBuffer bb;
    Message msg;

    @Override
    public void run() {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          udpSocket.receive(pkt);
        } catch (IOException ex) {
          logger.error(ex);
        }
        //((SkyMote)sim.getMotes()[data[0]]).getCPU().getIOUnit("ADC12");
        bb = ByteBuffer.wrap(data);
        msg = Message.getRootAsMessage(bb);

        switch (msg.type()) {
          case (MsgType.PAUSE): {
            sim.stopSimulation();
          }
          case (MsgType.RESUME): {
            sim.resumeSimulation();
          }
          case (MsgType.PIR): {
            // Check we have a mote matching the ID
            if (sim.getMotes().length < msg.id()) {
              logger.info("No mote for id: " + msg.id());
              break;
            }
            // Update it in seperate simulation thread.
            toRun = new Runnable() {
              @Override
              public void run() {
                sim.getMotes()[msg.id()].getInterfaces().getButton().clickButton();
              }
            };
            break;
          }
          case (MsgType.LOCATION): {
            // Check we have a mote matching the ID
            if (sim.getMotes().length < msg.id()) {
              logger.info("No mote for id: " + msg.id());
              break;
            }
            // Update it in seperate simulation thread.
            toRun = new Runnable() {
              @Override
              public void run() {
                //logger.info("Got a location update for " + msg.id());
                // logger.info("X: " + msg.location().x() +
                //             " Y: " + msg.location().y() +
                //             " Z: " + msg.location().z());
                sim.getMotes()[msg.id()].getInterfaces().getPosition().
                        setCoordinates(msg.location().x()/100,
                                       msg.location().y()/100,
                                       msg.location().z()/100);
              }
            };
            break;
          }
          default: {
            logger.info("Message type not recognised");
          }
        }
        if (toRun != null) sim.invokeSimulationThread(toRun);
        toRun = null;
      }
      logger.info("Interrupted: Exited UDP SOCKET read loop, closing socket");
      udpSocket.close();
    }
    public void interrupt() {
      super.interrupt();
      udpSocket.close();
      logger.info("Socket Closed");
    }
  }
}
