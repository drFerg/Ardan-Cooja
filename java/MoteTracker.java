import org.apache.log4j.Logger;
import java.util.Observable;
import java.util.Observer;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;

public class MoteTracker implements Observer {
  public enum RadioState {
    IDLE, RECEIVING, TRANSMITTING, INTERFERED
  }
  /* last radio state */
  private boolean radioWasOn;
  private RadioState lastRadioState;
  private long lastUpdateTime;

  /* accumulating radio state durations */
  long duration = 0;
  long radioOn = 0;
  long radioTx = 0;
  long radioRx = 0;
  long radioInterfered = 0;

  private Simulation simulation;
  private Mote mote;
  private Radio radio;

  public MoteTracker(Mote mote) {
    this.simulation = mote.getSimulation();
    this.mote = mote;
    this.radio = mote.getInterfaces().getRadio();

    radioWasOn = radio.isRadioOn();
    if (radio.isTransmitting()) {
      lastRadioState = RadioState.TRANSMITTING;
    } else if (radio.isReceiving()) {
      lastRadioState = RadioState.RECEIVING;
    } else if (radio.isInterfered()){
      lastRadioState = RadioState.INTERFERED;
    } else {
      lastRadioState = RadioState.IDLE;
    }
    lastUpdateTime = simulation.getSimulationTime();

    radio.addObserver(this);
  }

  public void update(Observable o, Object arg) {
    update();
  }
  public void update() {
    long now = simulation.getSimulationTime();

    accumulateDuration(now - lastUpdateTime);

    /* Radio on/off */
    if (radioWasOn) {
      accumulateRadioOn(now - lastUpdateTime);
    }

    /* Radio tx/rx */
    if (lastRadioState == RadioState.TRANSMITTING) {
      accumulateRadioTx(now - lastUpdateTime);
    } else if (lastRadioState == RadioState.RECEIVING) {
      accumulateRadioRx(now - lastUpdateTime);
    } else if (lastRadioState == RadioState.INTERFERED) {
      accumulateRadioIntefered(now - lastUpdateTime);
    }

    /* Await next radio event */
    if (radio.isTransmitting()) {
      lastRadioState = RadioState.TRANSMITTING;
    } else if (!radio.isRadioOn()) {
      lastRadioState = RadioState.IDLE;
    } else if (radio.isInterfered()) {
      lastRadioState = RadioState.INTERFERED;
    } else if (radio.isReceiving()) {
      lastRadioState = RadioState.RECEIVING;
    } else {
      lastRadioState = RadioState.IDLE;
    }
    radioWasOn = radio.isRadioOn();
    lastUpdateTime = now;
  }

  protected void accumulateDuration(long t) {
    duration += t;
  }
  protected void accumulateRadioOn(long t) {
    radioOn += t;
  }
  protected void accumulateRadioTx(long t) {
    radioTx += t;
  }
  protected void accumulateRadioRx(long t) {
    radioRx += t;
  }
  protected void accumulateRadioIntefered(long t) {
    radioInterfered += t;
  }

  public double getRadioOnRatio() {
    return 1.0*radioOn/duration;
  }

  public double getRadioTxRatio() {
    return 1.0*radioTx/duration;
  }

  public double getRadioInterferedRatio() {
    return 1.0*radioInterfered/duration;
  }

  public double getRadioRxRatio() {
    return 1.0*radioRx/duration;
  }

  public Mote getMote() {
    return mote;
  }

  public void dispose() {
    radio.deleteObserver(this);
    radio = null;
    mote = null;
  }

  public String toString() {
    return toString(true, true);
  }
  public String toString(boolean radioHW, boolean radioRXTX) {
    StringBuilder sb = new StringBuilder();
    String moteString = mote.toString().replace(' ', '_');

    sb.append(moteString + " MONITORED " + duration + " us\n");
    if (radioHW) {
      sb.append(String.format(moteString + " ON " + (radioOn + " us ") + "%2.2f %%", 100.0*getRadioOnRatio()) + "\n");
    }
    if (radioRXTX) {
      sb.append(String.format(moteString + " TX " + (radioTx + " us ") + "%2.2f %%", 100.0*getRadioTxRatio()) + "\n");
      sb.append(String.format(moteString + " RX " + (radioRx + " us ") + "%2.2f %%", 100.0*getRadioRxRatio()) + "\n");
      sb.append(String.format(moteString + " INT " + (radioInterfered + " us ") + "%2.2f %%", 100.0*getRadioInterferedRatio()) + "\n");
    }
    return sb.toString();
  }
}
