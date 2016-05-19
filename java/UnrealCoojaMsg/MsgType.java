// automatically generated, do not modify

package UnrealCoojaMsg;

public final class MsgType {
  private MsgType() { }
  public static final byte LED = 1;
  public static final byte LOCATION = 2;
  public static final byte RADIO = 3;
  public static final byte PIR = 4;
  public static final byte PAUSE = 5;
  public static final byte RESUME = 6;

  private static final String[] names = { "LED", "LOCATION", "RADIO", "PIR", "PAUSE", "RESUME", };

  public static String name(int e) { return names[e - LED]; }
};

