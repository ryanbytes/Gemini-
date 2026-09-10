package com.ryanbytes.geminimdiu;

import java.util.Arrays;

/**
 * Gemini OBC CPU core derived from the execution model in Virtual AGC yaOBC.
 * Virtual AGC is GPL-2.0-or-later; this translated core is distributed under
 * the same terms. This is a CPU emulator, not recovered Gemini flight software.
 */
public final class GeminiObcCore {
    public static final int MASK13 = 0x1FFF;
    public static final int SIGN13 = 0x1000;
    public static final int MASK26 = 0x03FFFFFF;
    public static final int SIGN26 = 0x02000000;
    public static final int RESIDUAL_SECTOR = 15;
    public static final long NANOS_PER_CYCLE = 140_000L;

    public static final int HOP=0, DIV=1, PRO=2, RSU=3, ADD=4, SUB=5, CLA=6, AND=7,
            MPY=8, TRA=9, SHF=10, TMI=11, STO=12, SPQ=13, CLD=14, TNZ=15;

    private static final int PRO_ILLEGAL=0, PRO_TBD=1, PRO_OUTPUT=2, PRO_INPUT=3,
            PRO_SPECIAL=4;
    private static final int[] PRO_DIRECTION = new int[64];
    static {
        Arrays.fill(PRO_DIRECTION, PRO_ILLEGAL);
        int[] outputs = {
                001,002,003,004,005,007,010,011,012,013,014,017,021,022,023,025,027,
                030,031,032,033,034,035,040,041,044,050,051,052,060,063,065
        };
        int[] inputs = {000,036,043,045,046,056,062};
        int[] tbd = {055,061,064};
        for(int x:outputs) PRO_DIRECTION[x]=PRO_OUTPUT;
        for(int x:inputs) PRO_DIRECTION[x]=PRO_INPUT;
        for(int x:tbd) PRO_DIRECTION[x]=PRO_TBD;
        PRO_DIRECTION[015]=PRO_SPECIAL;
        PRO_DIRECTION[020]=PRO_SPECIAL;
    }

    public static final class Hop {
        public int page;
        public int syllable;
        public int word;
        public boolean halfWordMode;
        Hop copy(){ Hop h=new Hop(); h.page=page;h.syllable=syllable;h.word=word;h.halfWordMode=halfWordMode;return h; }
    }

    private final int[][][] memory = new int[16][3][256];
    private final int[] pro = new int[64];
    private final boolean[] cld = new boolean[64];
    private final Hop hop = new Hop();
    private int accumulator;
    private int pq;
    private int pqDelay;
    private long cycles;
    private boolean endOfSector;
    private String fault;

    public GeminiObcCore() {
        for(int p=0;p<16;p++) for(int s=0;s<3;s++) Arrays.fill(memory[p][s], -1);
    }

    public static GeminiObcCore createDiagnostic() {
        GeminiObcCore c = new GeminiObcCore();
        c.setHop(0,0,0,false);
        c.setInstruction(0,0,0,CLA,0200);
        c.setInstruction(0,0,1,ADD,0201);
        c.setInstruction(0,0,2,STO,0200);
        c.setInstruction(0,0,3,TRA,0000);
        c.setData26(0,0200,0);
        c.setData26(0,0201,1);
        return c;
    }

    public void reset() {
        accumulator=0; pq=0; pqDelay=0; cycles=0; endOfSector=false; fault=null;
        setHop(0,0,0,false);
    }

    public void setHop(int page,int syllable,int word,boolean hwm) {
        hop.page=page&15; hop.syllable=syllable&3; hop.word=word&0x1FF; hop.halfWordMode=hwm;
        endOfSector=false;
    }
    public Hop getHop(){ return hop.copy(); }
    public long getCycles(){ return cycles; }
    public int getAccumulatorRaw(){ return accumulator & MASK26; }
    public int getAccumulatorSigned(){ return sign26(accumulator); }
    public int getPqRaw(){ return pq & MASK26; }
    public int getPqDelay(){ return pqDelay; }
    public boolean isEndOfSector(){ return endOfSector; }
    public String getFault(){ return fault; }
    public void clearFault(){ fault=null; }

    public void setPro(int yx,int value){ pro[yx&077]=value&MASK26; }
    public int getPro(int yx){ return pro[yx&077]&MASK26; }
    public void setCld(int yx,boolean value){ cld[yx&077]=value; }
    public boolean getCld(int yx){ return cld[yx&077]; }

    public void setInstruction(int page,int syllable,int word,int opcode,int operand){
        memory[page&15][syllable%3][word&0xFF]=((opcode&15)<<9)|(operand&0x1FF);
    }
    public int getInstruction(int page,int syllable,int word){ return memory[page&15][syllable%3][word&0xFF]; }
    public void setData26(int page,int word,int value){ int v=value&MASK26; memory[page&15][0][word&0xFF]=v&MASK13; memory[page&15][1][word&0xFF]=(v>>>13)&MASK13; }
    public int getData26Raw(int page,int word){ return word26(page&15,word&0xFF); }
    public void setData13(int page,int word,int value){ memory[page&15][2][word&0xFF]=value&MASK13; }

    public static int encodeHop(int page,int syllable,int word,boolean hwm){
        int raw=word&0777; raw|=(page&017)<<9; raw|=(syllable&03)<<14; if(hwm) raw|=1<<17; return raw;
    }
    public static Hop decodeHop(int raw){ Hop h=new Hop();h.word=raw&0777;h.page=(raw>>>9)&017;h.syllable=(raw>>>14)&03;h.halfWordMode=((raw>>>17)&1)!=0;return h; }

    public void runCycles(int n){ for(int i=0;i<n && !endOfSector && fault==null;i++) step(); }

    public boolean step() {
        if(endOfSector) return false;
        int fetchSector=(hop.word&0400)!=0?RESIDUAL_SECTOR:hop.page;
        if(hop.syllable<0||hop.syllable>2){fault="invalid syllable";return false;}
        int instruction=memory[fetchSector][hop.syllable][hop.word&0377];
        if(instruction<0){fault="execute from uninitialized memory";return false;}
        int opcode=(instruction>>>9)&017;
        int value=instruction&0777;
        boolean hwm=hop.halfWordMode;
        int dataSector=(value&0400)!=0?RESIDUAL_SECTOR:hop.page;
        int dataWord=value&0377;
        int data;
        if(hwm){ int v=word13(dataSector,2,dataWord); data=(v&SIGN13)!=0 ? v|~MASK13 : v; }
        else { int v=word26(dataSector,dataWord); data=(v&SIGN26)!=0 ? v|~MASK26 : v; }

        boolean jumped=false;
        Hop jump=null;
        switch(opcode){
            case HOP: jump=decodeHop(data); jumped=true; break;
            case DIV: { int divisor=data; int dividend=accumulator&MASK26; int q=(divisor==0)?0:dividend/divisor; pq=q&MASK26; pqDelay=5; break; }
            case PRO: {
                boolean clear=(value&0400)!=0; int yx=value&077; int direction=PRO_DIRECTION[yx];
                if(direction==PRO_OUTPUT){ pro[yx]=accumulator&MASK26; if(clear) accumulator=0; }
                else if(direction==PRO_INPUT){ if(clear) accumulator=0; accumulator=(accumulator|pro[yx])&MASK26; }
                else fault=(direction==PRO_TBD)?"unimplemented PRO YX":"illegal/special PRO YX";
                break;
            }
            case RSU: accumulator=(data-sign26(accumulator))&MASK26; break;
            case ADD: accumulator=(sign26(accumulator)+data)&MASK26; break;
            case SUB: accumulator=(sign26(accumulator)-data)&MASK26; break;
            case CLA: accumulator=data&MASK26; break;
            case AND: accumulator=(accumulator&data)&MASK26; break;
            case MPY: { long product=(long)(accumulator&MASK26)*(long)data; pq=(int)product&MASK26; pqDelay=3; break; }
            case TRA: jump=hop.copy(); if((value&0400)!=0) jump.page=RESIDUAL_SECTOR; jump.word=value&0377; jumped=true; break;
            case SHF: {
                int v=sign26(accumulator);
                if(value==021) v>>=1; else if(value==020) v>>=2;
                else if(value>=030&&value<=037) v<<=1; else if(value>=040&&value<=047) v<<=2; else v=0;
                accumulator=v&MASK26; break;
            }
            case TMI: if((accumulator&SIGN26)!=0){jump=hop.copy();if((value&0400)!=0)jump.page=RESIDUAL_SECTOR;jump.word=value&0377;jumped=true;} break;
            case STO: if(!hwm) setData26(dataSector,dataWord,accumulator); break;
            case SPQ: if(!hwm) setData26(dataSector,dataWord,pq); break;
            case CLD: accumulator=cld[value&077]?MASK26:0; break;
            case TNZ: if((accumulator&MASK26)!=0){jump=hop.copy();if((value&0400)!=0)jump.page=RESIDUAL_SECTOR;jump.word=value&0377;jumped=true;} break;
            default: fault="invalid opcode"; break;
        }

        cycles++;
        if(jumped) { hop.page=jump.page;hop.syllable=jump.syllable;hop.word=jump.word;hop.halfWordMode=jump.halfWordMode; }
        else if((hop.word&0377)<0377) hop.word++;
        else endOfSector=true;
        if(pqDelay>0) pqDelay--;
        return fault==null;
    }

    private int word13(int page,int syllable,int word){ int v=memory[page&15][syllable][word&0xFF]; return v<0?MASK13:v&MASK13; }
    private int word26(int page,int word){ return word13(page,0,word)|(word13(page,1,word)<<13); }
    private static int sign26(int v){ v&=MASK26; return (v&SIGN26)!=0?v|~MASK26:v; }
}
