// automatically generated, do not modify

package UnrealCoojaMsg;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class RadioDuty extends Struct {
  public RadioDuty __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public double radioOnRatio() { return bb.getDouble(bb_pos + 0); }
  public double radioTxRatio() { return bb.getDouble(bb_pos + 8); }
  public double radioRxRatio() { return bb.getDouble(bb_pos + 16); }
  public double radioInterferedRatio() { return bb.getDouble(bb_pos + 24); }

  public static int createRadioDuty(FlatBufferBuilder builder, double radioOnRatio, double radioTxRatio, double radioRxRatio, double radioInterferedRatio) {
    builder.prep(8, 32);
    builder.putDouble(radioInterferedRatio);
    builder.putDouble(radioRxRatio);
    builder.putDouble(radioTxRatio);
    builder.putDouble(radioOnRatio);
    return builder.offset();
  }
};
