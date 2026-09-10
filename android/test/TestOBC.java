import com.ryanbytes.geminimdiu.GeminiObcCore;

public class TestOBC {
  public static void main(String[] args){
    GeminiObcCore c=GeminiObcCore.createDiagnostic();
    c.runCycles(400);
    if(c.getFault()!=null) throw new AssertionError(c.getFault());
    if(c.getAccumulatorRaw()!=100) throw new AssertionError("ACC="+c.getAccumulatorRaw());
    if(c.getData26Raw(0,0200)!=100) throw new AssertionError("MEM="+c.getData26Raw(0,0200));
    if(c.getCycles()!=400) throw new AssertionError("cycles="+c.getCycles());

    int raw=GeminiObcCore.encodeHop(7,2,0123,true);
    GeminiObcCore.Hop h=GeminiObcCore.decodeHop(raw);
    if(h.page!=7||h.syllable!=2||h.word!=0123||!h.halfWordMode) throw new AssertionError("HOP codec");

    GeminiObcCore b=new GeminiObcCore();
    b.setHop(0,0,0,false);
    b.setData26(0,0200,-1);
    b.setInstruction(0,0,0,GeminiObcCore.CLA,0200);
    b.setInstruction(0,0,1,GeminiObcCore.TMI,0003);
    b.setInstruction(0,0,2,GeminiObcCore.CLA,0201);
    b.setInstruction(0,0,3,GeminiObcCore.CLA,0202);
    b.setData26(0,0201,7);
    b.setData26(0,0202,9);
    b.runCycles(3);
    if(b.getAccumulatorRaw()!=9) throw new AssertionError("TMI");
    System.out.println("OBC CORE TESTS PASSED");
  }
}
