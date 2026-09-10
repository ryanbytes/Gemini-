// Gemini OBC execution core translated from the Virtual AGC yaOBC execution model.
// Virtual AGC is GPL-2.0-or-later. This is a CPU emulator, not recovered flight software.
export class GeminiObcCore {
  static MASK13=0x1fff; static SIGN13=0x1000;
  static MASK26=0x03ffffff; static SIGN26=0x02000000;
  static RESIDUAL=15;
  static HOP=0; static DIV=1; static PRO=2; static RSU=3; static ADD=4; static SUB=5; static CLA=6; static AND=7;
  static MPY=8; static TRA=9; static SHF=10; static TMI=11; static STO=12; static SPQ=13; static CLD=14; static TNZ=15;

  constructor(){
    this.memory=Array.from({length:16},()=>Array.from({length:3},()=>new Int32Array(256).fill(-1)));
    this.pro=new Int32Array(64); this.cld=new Uint8Array(64);
    this.hop={page:0,syllable:0,word:0,hwm:false}; this.acc=0; this.pq=0; this.pqDelay=0; this.cycles=0; this.fault=null; this.end=false;
  }
  static diagnostic(){
    const c=new GeminiObcCore(); c.setHop(0,0,0,false);
    c.setInstruction(0,0,0,this.CLA,0o200); c.setInstruction(0,0,1,this.ADD,0o201);
    c.setInstruction(0,0,2,this.STO,0o200); c.setInstruction(0,0,3,this.TRA,0);
    c.setData26(0,0o200,0); c.setData26(0,0o201,1); return c;
  }
  setHop(page,syllable,word,hwm=false){this.hop={page:page&15,syllable:syllable&3,word:word&0x1ff,hwm:!!hwm};this.end=false;}
  setInstruction(page,syllable,word,op,operand){this.memory[page&15][syllable%3][word&255]=((op&15)<<9)|(operand&0x1ff);}
  setData26(page,word,value){const v=value&GeminiObcCore.MASK26;this.memory[page&15][0][word&255]=v&GeminiObcCore.MASK13;this.memory[page&15][1][word&255]=(v>>>13)&GeminiObcCore.MASK13;}
  getData26(page,word){return this.word13(page,0,word)|(this.word13(page,1,word)<<13);}
  setData13(page,word,value){this.memory[page&15][2][word&255]=value&GeminiObcCore.MASK13;}
  static encodeHop(page,syllable,word,hwm=false){return (word&0x1ff)|((page&15)<<9)|((syllable&3)<<14)|(hwm?(1<<17):0);}
  static decodeHop(raw){return {word:raw&0x1ff,page:(raw>>>9)&15,syllable:(raw>>>14)&3,hwm:((raw>>>17)&1)!==0};}
  sign26(v){v&=GeminiObcCore.MASK26;return (v&GeminiObcCore.SIGN26)?(v|~GeminiObcCore.MASK26):v;}
  word13(page,syllable,word){const v=this.memory[page&15][syllable][word&255];return v<0?GeminiObcCore.MASK13:v&GeminiObcCore.MASK13;}
  runCycles(n){for(let i=0;i<n&&!this.end&&!this.fault;i++)this.step();}
  branch(value){const h={...this.hop};if(value&0o400)h.page=GeminiObcCore.RESIDUAL;h.word=value&0o377;return h;}
  proDirection(yx){
    const outputs=new Set([0o01,0o02,0o03,0o04,0o05,0o07,0o10,0o11,0o12,0o13,0o14,0o17,0o21,0o22,0o23,0o25,0o27,0o30,0o31,0o32,0o33,0o34,0o35,0o40,0o41,0o44,0o50,0o51,0o52,0o60,0o63,0o65]);
    const inputs=new Set([0o00,0o36,0o43,0o45,0o46,0o56,0o62]);
    if(outputs.has(yx))return 'out';if(inputs.has(yx))return 'in';return 'bad';
  }
  step(){
    if(this.end||this.fault)return false;
    const sec=(this.hop.word&0o400)?GeminiObcCore.RESIDUAL:this.hop.page;
    const ins=this.memory[sec][this.hop.syllable]?.[this.hop.word&0o377];
    if(ins==null||ins<0){this.fault='execute from uninitialized memory';return false;}
    const op=(ins>>>9)&15, value=ins&0o777, ds=(value&0o400)?GeminiObcCore.RESIDUAL:this.hop.page, dw=value&0o377;
    let data;
    if(this.hop.hwm){let v=this.word13(ds,2,dw);data=(v&GeminiObcCore.SIGN13)?(v|~GeminiObcCore.MASK13):v;}
    else {let v=this.getData26(ds,dw);data=(v&GeminiObcCore.SIGN26)?(v|~GeminiObcCore.MASK26):v;}
    let jump=null;
    switch(op){
      case GeminiObcCore.HOP: jump=GeminiObcCore.decodeHop(data); break;
      case GeminiObcCore.DIV: this.pq=((data===0?0:Math.trunc((this.acc&GeminiObcCore.MASK26)/data))&GeminiObcCore.MASK26);this.pqDelay=5;break;
      case GeminiObcCore.PRO:{const yx=value&0o77,dir=this.proDirection(yx),clear=(value&0o400)!==0;if(dir==='out'){this.pro[yx]=this.acc&GeminiObcCore.MASK26;if(clear)this.acc=0;}else if(dir==='in'){if(clear)this.acc=0;this.acc=(this.acc|this.pro[yx])&GeminiObcCore.MASK26;}else this.fault='illegal/special PRO YX';break;}
      case GeminiObcCore.RSU:this.acc=(data-this.sign26(this.acc))&GeminiObcCore.MASK26;break;
      case GeminiObcCore.ADD:this.acc=(this.sign26(this.acc)+data)&GeminiObcCore.MASK26;break;
      case GeminiObcCore.SUB:this.acc=(this.sign26(this.acc)-data)&GeminiObcCore.MASK26;break;
      case GeminiObcCore.CLA:this.acc=data&GeminiObcCore.MASK26;break;
      case GeminiObcCore.AND:this.acc=(this.acc&data)&GeminiObcCore.MASK26;break;
      case GeminiObcCore.MPY:this.pq=(Math.imul(this.acc&GeminiObcCore.MASK26,data))&GeminiObcCore.MASK26;this.pqDelay=3;break;
      case GeminiObcCore.TRA:jump=this.branch(value);break;
      case GeminiObcCore.SHF:{let v=this.sign26(this.acc);if(value===0o21)v>>=1;else if(value===0o20)v>>=2;else if(value>=0o30&&value<=0o37)v<<=1;else if(value>=0o40&&value<=0o47)v<<=2;else v=0;this.acc=v&GeminiObcCore.MASK26;break;}
      case GeminiObcCore.TMI:if(this.acc&GeminiObcCore.SIGN26)jump=this.branch(value);break;
      case GeminiObcCore.STO:if(!this.hop.hwm)this.setData26(ds,dw,this.acc);break;
      case GeminiObcCore.SPQ:if(!this.hop.hwm)this.setData26(ds,dw,this.pq);break;
      case GeminiObcCore.CLD:this.acc=this.cld[value&0o77]?GeminiObcCore.MASK26:0;break;
      case GeminiObcCore.TNZ:if(this.acc&GeminiObcCore.MASK26)jump=this.branch(value);break;
      default:this.fault='invalid opcode';
    }
    this.cycles++;
    if(jump)this.hop=jump;else if((this.hop.word&0o377)<0o377)this.hop.word++;else this.end=true;
    if(this.pqDelay>0)this.pqDelay--;
    return !this.fault;
  }
}
