import UnrealCoojaMsg.Message;
import UnrealCoojaMsg.MsgType;
import UnrealCoojaMsg.RadioDuty;
import UnrealCoojaMsg.SimState;
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
public class UnrealCooja extends VisPlugin implements Observer{
  // private final static String BOOTSTRAP_SERVERS = "146.169.15.97:9092";
  private final static String BOOTSTRAP_SERVERS = "localhost:9092";

  private static final long serialVersionUID = 4368807123350830772L;
  private static Logger logger = Logger.getLogger(UnrealCooja.class);
  private Thread kafkaConsumer;
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
      producer.send(new ProducerRecord<String, byte[]>("radio", "", data));
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
  }

  public void initObservers() {

    /* Check for class loaders, if not the same class casting won't work, reload to fix */
    if ((sim.getMotes()[0].getClass().getClassLoader() != this.getClass().getClassLoader()) &&
        (sim.getMotes()[0].getClass().getClassLoader() != gui.getClass().getClassLoader())) {
          logger.info("Different class loaders - Reload Cooja to fix");
          return;
    }

    hostPort = Integer.parseInt(hostPortField.getText());
    clientPort = Integer.parseInt(clientPortField.getText());
    try {
      clientIPAddr = InetAddress.getByName(ipAddrField.getText());
    } catch (Exception e) {
      logger.error("UnrealCooja>> " + e.getMessage());

    }
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
    //props.put("acks", "0");
    //props.put("retries", 0);
    //props.put("batch.num.messages", 1);
    props.put("linger.ms", 0);
    // props.put(ConsumerConfig.QUEUE_BUFFERING_MAX_MS, 10);
    // Create the consumer using props.
    producer = new KafkaProducer<>(props);


    initialised = true;
    networkObserver = new RadioMediumEventObserver(radioMedium, producer);
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


    kafkaConsumer = new CoojaKafkaConsumer();
    kafkaConsumer.start();

    addSecondObserver(this);
  }

  /* Adds a new mote to the observed set of motes
   * Needed for use in listener, to access /this/ context */
  public void addMote(Mote mote){
    moteObservers.add(new MoteObserver(sim, mote, producer));
    moteTrackers.add(new MoteTracker(mote));
  }

  public void closePlugin() {
    /* Clean up plugin resources */
    logger.info("Stopping KafkaConsumer...");
    kafkaConsumer.interrupt();
    try {
      kafkaConsumer.join();
    } catch (InterruptedException e) {
      logger.info("Interrupted whilst waiting for kafkaConsumer");
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

  public void cpuEventHandler(MSP430 cpu, Mote mote){
//    hwdb.insertLater(String.format("insert into cpu values ('%d', '%d', '%d', \"%s\")\n", sim.getSimulationTime(), mote.getID(),
//                 cpu.getMode(), MSP430Constants.MODE_NAMES[cpu.getMode()]));
  }

  public void updateSimState(Message msg){
    switch (msg.type()){
      case (SimState.NORMAL): {
        sim.setSpeedLimit(1.0);
        break;
      }
      case (SimState.SLOW): {
        sim.setSpeedLimit(0.1);
        break;
      }
      case (SimState.PAUSE): {
        sim.stopSimulation();
        break;
      }
      case (SimState.RESUME): {
        sim.startSimulation();
        break;
      }
      case (SimState.DOUBLE): {
        sim.setSpeedLimit(2.0);
        break;
      }
    }
  }

  public Runnable updateSensorState(final Message msg) {
    // Check we have a mote matching the ID
    if (sim.getMotes().length <= msg.id()) {
      logger.info("No mote for id: " + msg.id());
      return null;
    }
    // Interact with mote in seperate simulation thread.
    return new Runnable() {
      @Override
      public void run() {
        sim.getMotes()[msg.id()].getInterfaces().getButton().clickButton();
      }
    };
  }
  public Runnable updateLocation(final Message msg) {
    // Check we have a mote matching the ID
    if (sim.getMotes().length <= msg.id()) {
      logger.info("No mote for id: " + msg.id());
      return null;
    }
    // Update it in seperate simulation thread.
    return new Runnable() {
      @Override
      public void run() {
        sim.getMotes()[msg.id()].getInterfaces().getPosition().
                setCoordinates(msg.location().x()/100,
                               msg.location().y()/100,
                               msg.location().z()/100);
     // logger.info("Got a location update for " + msg.id());
     // logger.info("X: " + msg.location().x() +
     //             " Y: " + msg.location().y() +
     //             " Z: " + msg.location().z());
      }
    };
  }

  /* Forward data: Unreal Engine Mote -> Cooja mote */
  private class CoojaKafkaConsumer extends Thread {
    private final static String TOPIC = "sensor";
    // private final static String BOOTSTRAP_SERVERS = "146.169.15.97:9092";
    private final static String BOOTSTRAP_SERVERS = "localhost:9092";

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
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 0);
        // Set max wait time for additional events
        props.put("socket.blocking.max.ms", 0);
        // props.put(ConsumerConfig.QUEUE_BUFFERING_MAX_MS, 10);

        // Create the consumer using props.
        final Consumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        // Subscribe to the topic.
        consumer.subscribe(Collections.singletonList(TOPIC));
        return consumer;

    }

    @Override
    public void run() {
      System.out.println("Running consumer...");
      final Consumer<String, byte[]> consumer = createConsumer();
      Runnable toRun = null;
      try {
        while (!Thread.currentThread().isInterrupted()) {
          for (ConsumerRecord <String, byte[]> event : consumer.poll(1)) {
            System.out.println(event.key() + " >TIMESTAMP: " + event.timestamp());

            final Message msg = Message.getRootAsMessage(ByteBuffer.wrap(event.value()));
            toRun = null;
            switch (msg.type()) {
              case (MsgType.SIMSTATE): {
                updateSimState(msg);
                break;
              }
              case (MsgType.BUTTON):
              case (MsgType.PIR):
              case (MsgType.FIRE): {
                toRun = updateSensorState(msg);
                break;
              }
              case (MsgType.LOCATION): {
                toRun = updateLocation(msg);
              }
              default: {
                logger.info("Message type not supported: " + MsgType.name(msg.type()));
              }
            }
            if (toRun != null) {
              sim.invokeSimulationThread(toRun);
            }
            logger.info("RECV: " + sim.getSimulationTime());
          }
        }
    } catch (Exception e) {
      System.out.println(e);
    }
      consumer.close();
      logger.info("Interrupted: Exited KafkaConsumer read loop");
    }

    public void interrupt() {
      super.interrupt();
      logger.info("KafkaConsumer stopped");
    }
  }
}
