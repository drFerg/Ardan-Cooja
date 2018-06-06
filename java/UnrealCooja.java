import UnrealCoojaMsg.Message;
import UnrealCoojaMsg.MsgType;
import UnrealCoojaMsg.RadioDuty;
import com.google.flatbuffers.*;
import java.util.Collections;

import java.util.Properties;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.BoxLayout;

import org.apache.log4j.Logger;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.SimEventCentral.MoteCountListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.mspmote.MspMote;
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
public class UnrealCooja extends VisPlugin implements CoojaEventObserver, Observer{
  private final static String BOOTSTRAP_SERVERS = "146.169.15.97:9092";
  private static final long serialVersionUID = 4368807123350830772L;
  private static Logger logger = Logger.getLogger(UnrealCooja.class);
  private Thread udpHandler;
  private DatagramSocket udpSocket;
  private int clientPort = 5000;
  private static String clientIPAddrStr = "localhost";
  private InetAddress clientIPAddr;
  private Producer<String, byte[]> producer;

  private int hostPort = 5011;
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

  private JTextField hostPortField;
  private JTextField clientPortField;
  private JTextField ipAddrField;

  private ArrayList<MoteTracker> moteTrackers;
  private static final long SECOND = 1000 * Simulation.MILLISECOND;
  private boolean hasSecondObservers = false;
  private SecondObservable secondObservable = new SecondObservable();
  private class SecondObservable extends Observable {
   private void newSecond(long time) {
     setChanged();
     notifyObservers(time);
    }
  }

  public void addSecondObserver(Observer newObserver) {
    secondObservable.addObserver(newObserver);
    hasSecondObservers = true;

    sim.invokeSimulationThread(new Runnable() {
      public void run() {
        if (!secondEvent.isScheduled()) {
          sim.scheduleEvent(
              secondEvent,
              sim.getSimulationTime() - (sim.getSimulationTime() % SECOND) + SECOND);
        }
      }
    });
  }

  private TimeEvent secondEvent = new TimeEvent(0) {
      public void execute(long t) {
        if (!hasSecondObservers) {
          return;
        }

        secondObservable.newSecond(sim.getSimulationTime());
        sim.scheduleEvent(this, t + SECOND);
        logger.info("Second event!");
      }
      public String toString() {
        return "SECOND: " + secondObservable.countObservers();
      }
  };
  /**
   * Delete millisecond observer.
   *
   * @see #addMillisecondObserver(Observer)
   * @param observer Observer to delete
   */
  public void deleteSecondObserver(Observer observer) {
    secondObservable.deleteObserver(observer);
    hasSecondObservers = secondObservable.countObservers() > 0;
  }


  /**
   * @param simulation Simulation object
   * @param gui GUI object
   */
  public UnrealCooja(Simulation simulation, Cooja gui) {
    super("Unreal Cooja", gui, false);
    sim = simulation;
    radioMedium = sim.getRadioMedium();
    this.gui = gui;
    //JPanel listPane = new JPanel();
    this.getContentPane().setLayout(new BoxLayout(this.getContentPane(), BoxLayout.PAGE_AXIS));

    /* Initialise Observers button */
    JButton button = new JButton("Observe");
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!initialised) initObservers();
      }
    });
    add(button);
    /* Text field */
    JLabel ipLabel = new JLabel("IP:");
    add(ipLabel);
    ipAddrField = new JTextField(clientIPAddrStr);
    add(ipAddrField);


    JLabel clientPortLabel = new JLabel("Client Port:");
    add(clientPortLabel);
    clientPortField = new JTextField("" + clientPort);
    add(clientPortField);

    JLabel portLabel = new JLabel("Host Port:");
    add(portLabel);
    hostPortField = new JTextField("" + hostPort);
    add(hostPortField);
    setSize(300, 300);
    insertBuffer = new ArrayList<String>();

  }

  public void update(Observable obs, Object obj) {
    DatagramPacket sendPacket;
    FlatBufferBuilder builder = new FlatBufferBuilder(1024);
    Message.startNodeVector(builder, moteTrackers.size());
    for (MoteTracker t: moteTrackers) {
        RadioDuty.createRadioDuty(builder,
          t.getRadioOnRatio(),
          t.getRadioTxRatio(),
          t.getRadioRxRatio(),
          t.getRadioInterferedRatio()
        );
    }
    int nodevec = builder.endVector();
    Message.startMessage(builder);
    Message.addType(builder, MsgType.RADIO_DUTY);
    Message.addNode(builder, nodevec);
    int msg = Message.endMessage(builder);
    builder.finish(msg);
    Message m = Message.getRootAsMessage(builder.dataBuffer());
    byte[] data = builder.sizedByteArray();
    try {
      producer.send(new ProducerRecord<String, byte[]>("sensor", "", data));
			// sendPacket = new DatagramPacket(data, data.length, clientIPAddr, clientPort);
      // logger.info("GOT DUTY");
			// udpSocket.send(sendPacket);
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
  }

  public void initObservers() {

    /* Check for class loaders, if not the same class casting won't work, reload to fix */
    if ((sim.getMotes()[0].getClass().getClassLoader() != this.getClass().getClassLoader()) &&
        (sim.getMotes()[0].getClass().getClassLoader() != gui.getClass().getClassLoader())) {
          logger.info("Different class loaders - Reload to fix");
          return;
    }

    hostPort = Integer.parseInt(hostPortField.getText());
    clientPort = Integer.parseInt(clientPortField.getText());
    try {
      clientIPAddr = InetAddress.getByName(ipAddrField.getText());
    } catch (Exception e) {
      logger.error("UnrealCooja>> " + e.getMessage());

    }
    initialised = true;
    networkObserver = new RadioMediumEventObserver(this, radioMedium, clientIPAddr, clientPort);
    /* Create observers for each mote */
    moteObservers = new ArrayList<MoteObserver>();
    moteTrackers = new ArrayList<MoteTracker>();
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



    try {
      udpSocket = new DatagramSocket(null);
      udpSocket.bind(new InetSocketAddress(hostPort));
      logger.info("Listening on port " + hostPort);
      Thread.currentThread().setContextClassLoader(null);

      final Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                  BOOTSTRAP_SERVERS);
      // props.put(ProducerConfig.GROUP_ID_CONFIG,
                                  // "rdkafka_consumer_example");
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "CoojaProducer");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
              StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            ByteArraySerializer.class.getName());
      // props.put(ConsumerConfig.QUEUE_BUFFERING_MAX_MS, 10);
      // Create the consumer using props.
      producer = new KafkaProducer<>(props);
    } catch (IOException e){
      logger.info("Couldn't open socket on port " + hostPort);
      logger.error(e.getMessage());
    }
    udpHandler = new IncomingDataHandler();
    udpHandler.start();

    addSecondObserver(this);
  }

  /* Adds a new mote to the observed set of motes
   * Needed for use in listener, to access /this/ context */
  public void addMote(Mote mote){
    moteObservers.add(new MoteObserver(this, mote, clientIPAddr, clientPort, producer));
    moteTrackers.add(new MoteTracker(mote));
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
    for (MoteTracker t: moteTrackers) {
      t.dispose();
      moteTrackers.remove(t);
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
    private final static String TOPIC = "sensor";
    private final static String BOOTSTRAP_SERVERS = "146.169.15.97:9092";

    // ByteBuffer bb;
    // Message msg;
    private Consumer<String, byte[]> createConsumer() {
        final Properties props = new Properties();
        Thread.currentThread().setContextClassLoader(null);
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                    BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                                    "rdkafka_consumer_example");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
              ByteArrayDeserializer.class.getName());
        // props.put(ConsumerConfig.QUEUE_BUFFERING_MAX_MS, 10);
        // Create the consumer using props.
        final Consumer<String, byte[]> consumer =
                                    new KafkaConsumer<>(props);
        // Subscribe to the topic.
        consumer.subscribe(Collections.singletonList(TOPIC));
        return consumer;

    }

    void runConsumer() throws InterruptedException {

    final Consumer<String, byte[]> consumer = createConsumer();
    final int giveUp = 100;   int noRecordsCount = 0;
    while (true) {
        final ConsumerRecords<String, byte[]> consumerRecords =
                consumer.poll(1000);
        if (consumerRecords.count()==0) {
            noRecordsCount++;
            if (noRecordsCount > giveUp) break;
            else continue;
        }
        // consumerRecords.forEach(record -> {
        //     System.out.printf("Consumer Record:(%d, %s, %d, %d)\n",
        //             record.key(), record.value(),
        //             record.partition(), record.offset());
        // });
        consumer.commitAsync();
    }
    consumer.close();
    System.out.println("DONE");
}
    @Override
    public void run() {
      System.out.println("Running consumer...");
      final Consumer<String, byte[]> consumer = createConsumer();
      try {
      while (!Thread.currentThread().isInterrupted()) {
        // System.out.println("Waiting for event...");

          final ConsumerRecords<String, byte[]> events = consumer.poll(100);


        // System.out.println("Got event(s)");
        for (ConsumerRecord <String, byte[]> event : events) {
          // System.out.println("Got event");
          // byte[] data = new byte[200];
          // DatagramPacket pkt = new DatagramPacket(data, 200);
          // try {
          //   udpSocket.receive(pkt);
          // } catch (IOException ex) {
          //   logger.error(ex);
          // }
          //((SkyMote)sim.getMotes()[data[0]]).getCPU().getIOUnit("ADC12");
          ByteBuffer bb = ByteBuffer.wrap(event.value());
          final Message msg = Message.getRootAsMessage(bb);
          Runnable toRun = null;
          switch (msg.type()) {
            case (MsgType.SPEED_NORM): {
              sim.setSpeedLimit(1.0);
              break;
            }
            case (MsgType.SPEED_SLOW): {
              sim.setSpeedLimit(0.1);
              break;
            }
            case (MsgType.PAUSE): {
              sim.stopSimulation();
              break;
            }
            case (MsgType.RESUME): {
              sim.startSimulation();
              break;
            }
            case (MsgType.PIR):
            case (MsgType.FIRE): {
              // Check we have a mote matching the ID
              System.out.println(sim.getMotes().length);
              System.out.println(msg.id());

              if (sim.getMotes().length <= msg.id()) {
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
              if (sim.getMotes().length <= msg.id()) {
                logger.info("No mote for id: " + msg.id());
                break;
              }
              // Update it in seperate simulation thread.
              toRun = new Runnable() {
                @Override
                public void run() {
                  // logger.info("Got a location update for " + msg.id());
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
        }
      }
    } catch (Exception e) {
      System.out.println(e);
    }
      consumer.close();
      logger.info("Interrupted: Exited UDP SOCKET read loop, closing socket");
      // udpSocket.close();
    }

    public void interrupt() {
      super.interrupt();
      // udpSocket.close();
      logger.info("Socket Closed");
    }
  }
}
