import com.ryanbytes.geminimdiu.MDIUModel;
import java.util.HashMap;
import java.util.Map;

public class TestMDIU {
  public static void main(String[] args) {
    Map<String,String> mem = new HashMap<>();
    MDIUModel m = new MDIUModel(new MDIUModel.Store(){
      public String get(String a){ return mem.get(a); }
      public void put(String a,String v){ mem.put(a,v); }
    });
    long t=0;
    m.clear(t);
    int[] digits={6,3,5,5,7,2,0};
    for(int d:digits){ if(!m.pressDigit(d,t)) throw new AssertionError("digit rejected"); t+=500; }
    if(!"6355720".equals(m.getDisplay())) throw new AssertionError(m.getDisplay());
    if(!m.enter(t)) throw new AssertionError("enter failed");

    // CLEAR resets input state but leaves the mechanical wheels untouched.
    String beforeClear=m.getDisplay();
    m.clear(t);
    if(!beforeClear.equals(m.getDisplay())) throw new AssertionError("CLEAR moved display wheels");
    // First digit is accepted immediately after CLEAR and only that wheel changes.
    if(!m.pressDigit(6,t)) throw new AssertionError("first digit after CLEAR rejected");
    if(!"6355720".equals(m.getDisplay())) throw new AssertionError("partial overwrite wrong: "+m.getDisplay());
    t+=500;
    if(!m.pressDigit(3,t)) throw new AssertionError(); t+=500;
    String[] r=m.readOut(t);
    if(r==null || !"55720".equals(r[1])) throw new AssertionError("read failed");
    for(int i=0;i<5;i++) m.setReadoutDigit(i+2,r[1].charAt(i));
    if(!"6355720".equals(m.getDisplay())) throw new AssertionError(m.getDisplay());

    m.clear(t);
    if(!m.pressDigit(0,t)) throw new AssertionError(); t+=500;
    if(!m.pressDigit(0,t)) throw new AssertionError(); t+=500;
    if(m.readOut(t)!=null || !"0000000".equals(m.getDisplay()) || !m.isError()) throw new AssertionError("error behavior");
    System.out.println("MDIU MODEL TESTS PASSED");
  }
}
