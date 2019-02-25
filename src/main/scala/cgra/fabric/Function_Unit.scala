// See README.md for license details.

package cgra.fabric

import cgra.config._
import chisel3._
import chisel3.util._
import tile._

class ALU (InstructionList : Array[Int],aluDataWidth : Int) extends Module {
  val io = IO(new Bundle{
    val operand1 = Flipped(DecoupledIO(UInt(aluDataWidth.W)))
    val operand2 = Flipped(DecoupledIO(UInt(aluDataWidth.W)))
    val opcode = Input(UInt({
      var opWidth = 0
      if (InstructionList.max == 0)
        opWidth = 1
      else if(InstructionList.max == 1)
        opWidth = 1
      else if(isPow2(InstructionList.max))
        opWidth = log2Ceil(InstructionList.max) + 1
      else
        opWidth = log2Ceil(InstructionList.max)
      opWidth
    }.W))
    val output_ports = DecoupledIO(UInt(aluDataWidth.W))
  }
  )
  val result = Wire(UInt(aluDataWidth.W))
  io.operand1.ready <> io.output_ports.ready
  io.operand2.ready <> io.output_ports.ready
  io.output_ports.bits <> result
  io.output_ports.valid <> false.B
  result := 0.U

  when(io.operand1.valid && io.operand2.valid){
    if (InstructionList.contains(isa.Or)){
      when(io.opcode === isa.Or.U){
        result := (io.operand1.bits | io.operand2.bits)
      }
    }
    if (InstructionList.contains(isa.And)){
      when(io.opcode === isa.And.U){
        result := (io.operand1.bits & io.operand2.bits)
      }
    }
    if (InstructionList.contains(isa.Add)){
      when(io.opcode === isa.Add.U){
        result := (io.operand1.bits + io.operand2.bits)
      }
    }
    if (InstructionList.contains(isa.Sub)){
      when(io.opcode === isa.Sub.U){
        result := (io.operand1.bits - io.operand2.bits)
      }
    }
    if (InstructionList.contains(isa.Mul)){
      when(io.opcode === isa.Mul.U){
        result := (io.operand1.bits * io.operand2.bits)
      }
    }
    if (InstructionList.contains(isa.UDiv)){
      when(io.opcode === isa.UDiv.U){
        result := (io.operand1.bits / io.operand2.bits)
      }
    }
    if (InstructionList.contains(isa.Xor)){
      when(io.opcode === isa.Xor.U){
        result := (io.operand1.bits ^ io.operand2.bits)
      }
    }
    printf(p"Operand 1 Bits inside ${io.operand1.bits}\n")
    printf(p"Operand 2 Bits inside ${io.operand2.bits}\n")
    printf(p"Result Wire inside ${result}\n")
    io.output_ports.valid <> true.B
  }

  // Debug
  printf(p"Operand 1 Ready ${io.operand1.ready}\n")
  printf(p"Operand 2 Ready ${io.operand2.ready}\n")
  printf(p"Result Wire ${result}\n")
  printf(p"Output Bits ${io.output_ports.bits}\n")
  printf(p"Output Valid ${io.output_ports.valid}\n")
  printf(">>--<<\n")
}

class DelayPipe (maxLength:Int,pipeDataWidth:Int) extends Module {
  val io = IO(new Bundle{
    val cfg_mode = Input(Bool())
    val delayLen = Input(UInt({
      if(maxLength == 0){
        1
      }
      else{
        log2Ceil(maxLength + 1)
      }
    }.W))
    val input_ports = Flipped(DecoupledIO(UInt(pipeDataWidth.W)))
    val output_ports = DecoupledIO(UInt(pipeDataWidth.W))
  })

  if(maxLength == 0){
    io.input_ports <> io.output_ports
  }
  else
  {
    val FIFO = RegInit(VecInit(Seq.fill(maxLength)(0.U(pipeDataWidth.W))))
    val FIFO_Valid = RegInit(VecInit(Seq.fill(maxLength)(false.B)))

    var delayRegWidth = 0
    if(maxLength == 0){
      delayRegWidth = 1
    }else{
      delayRegWidth = log2Ceil(maxLength + 1)
    }
    val delayReg = RegInit(0.U(delayRegWidth.W))

    when(io.cfg_mode) {
      // Reconfiguration
      // reconfig delay
      delayReg := io.delayLen
      // empty the FIFO
      for (i <- 0 until maxLength) {
        FIFO(i) := 0.U(pipeDataWidth.W)
        FIFO_Valid(i) := false.B
      }
    }.otherwise{
      when(io.output_ports.ready){
        for (i <- 0 until maxLength) {
          if(i < maxLength - 1)
            when((i + 1).U(delayRegWidth.W) === delayReg) {
              FIFO(i) := io.input_ports.bits
              FIFO_Valid(i) := io.input_ports.valid
            }.otherwise {
              FIFO(i) := FIFO(i + 1)
              FIFO_Valid(i) := FIFO_Valid(i + 1)
            }else{
            when((i + 1).U(delayRegWidth.W) === delayReg) {
              FIFO(i) := io.input_ports.bits
              FIFO_Valid(i) := io.input_ports.valid
            }.otherwise{
              FIFO(maxLength - 1) := 0.U
              FIFO_Valid(maxLength - 1) := false.B
            }
          }
        }
      }
    }
    io.output_ports.bits := Mux(delayReg === 0.U,io.input_ports.bits,FIFO(0))
    io.output_ports.valid := Mux(delayReg === 0.U,io.input_ports.valid,FIFO_Valid(0))
    io.input_ports.ready := io.output_ports.ready

    // FIFO debug
    printf("||||||||||\n")
    for(i <- 0 until maxLength){
      printf(p"FIFO($i) Bits = ${FIFO(i)} , ")
      printf(p"FIFO($i) Valid= ${FIFO_Valid(i)}\n")
    }
    printf("||||||||||\n")
    printf(p"Reg of Delay = $delayReg\n")
  }

  //debug
  printf(p"Output bits = ${io.output_ports.bits}\n")
  printf(p"Output valid = ${io.output_ports.valid}\n")
  printf(p"Input ready = ${io.input_ports.ready}\n")
}

class Function_Unit(
          numInput        : Int,
          numOutput       : Int,
          inputLocation  : Array[(Int,Int)],
          outputLocation : Array[(Int,Int)],
          deComp          : Int,
          Instructions    : Array[Array[Array[Int]]],
          //Instructions(outPort)(subNet) : Array of Instructions Set
          maxDelayPipeLen : Array[Array[Array[Int]]],
          //maxDelayPipeLen(outPort)(subNet)(operand)
          muxDirMatrix    : Array[Array[Array[Array[Boolean]]]],
          //muxDirMatrix(outPort)(subNet)(operand)(inPut)
          configsFromPort : Int,
          configsToPort   : Int
) extends FabricModule {
  //Override value
  override val datawidthModule: Int = fabricDataWidth
  override lazy val numModuleInput: Int = numInput
  override lazy val numModuleOutput: Int = numOutput
  override lazy val inputMoudleLocation: Array[(Int,Int)] = inputLocation
  override lazy val outputModuleLocation: Array[(Int,Int)] = outputLocation
  override lazy val numDecomp: Int = deComp
  override val configsModuleFromPort: Int = configsFromPort
  override val configsModuleToPort: Int = configsToPort

  val maxDelayLimitation = 16
  val maxDelay: Int = maxDelayPipeLen.map {
    _.map {
      _.max
    }.max
  }.max

  // Requirement check
  require(maxDelay <= maxDelayLimitation)
  require(numModuleOutput == muxDirMatrix.length)
  for (subNet <- 0 until numDecomp) {
    for (outPort <- 0 until this.numModuleOutput; operand <- 0 until 2) {
      if(numModuleInput != muxDirMatrix(outPort)(subNet)(operand).length){
        val foo = ""
      }

      require(numModuleInput == muxDirMatrix(outPort)(subNet)(operand).length, "Mux select Matrix size mismatch")
      require(muxDirMatrix(outPort)(subNet)(operand).exists(p => p),
        s"each output location need to have one input,Output ${outPort + 1} Sec ${subNet + 1}")
    }
    for (inPort <- 0 until this.numModuleInput; operand <- 0 until 2) {
      require(numModuleOutput == muxDirMatrix.map {
        _ (subNet)(operand)(inPort)
      }.length, "Mux select Matrix size mismatch")
      require(muxDirMatrix.flatMap {
        _ (subNet)
      }.map {
        _ (inPort)
      }.exists(p => p), s"each input location need to have one output,Input ${inPort + 1} Sec ${subNet + 1}")
    }
  }

  // Select Register definition
  val configPort = 0

  val SelReg = new Array[UInt](numModuleOutput * numDecomp * 2)
  val SelRegWidth = new Array[Int](numModuleOutput * numDecomp * 2)
  val selInsHigh: Int = {
    if(log2Ceil(numModuleOutput)==0)
      0
    else
      log2Ceil(numModuleOutput) - 1
  }
  val selInsLow = 0
  for (outPort <- 0 until numModuleOutput;
       subNet <- 0 until numDecomp;
       operand <- 0 until 2) {
    val numMuxIn: Int = muxDirMatrix(outPort)(subNet)(operand).count(p => p)

    SelReg(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort) =
      RegInit(0.U({
        if(isPow2(numMuxIn))
          log2Ceil(numMuxIn) + 1
        else if(numMuxIn == 1)
          1
        else
          log2Ceil(numMuxIn)
      }.W))
    SelRegWidth(numModuleOutput * numDecomp * operand +
      numModuleOutput * subNet +
      outPort) ={
      if(isPow2(numMuxIn))
        log2Ceil(numMuxIn) + 1
      else if(numMuxIn == 1)
        1
      else
        log2Ceil(numMuxIn)
    }
    when(io.cfg_mode) {
      SelReg(numModuleOutput * numDecomp * operand +
        numModuleOutput * subNet +
        outPort) :=
        io.input_ports(configPort).bits(selInsHigh, selInsLow)
    } //TODO: Currently from inPort(configPort)
    //TODO: How to update the register is not defined yet
    // todo(Instructions related)
  }

  // Delay Pipe Len Register definition
  val pipeLenReg = new Array[UInt](numModuleOutput * numDecomp * 2)
  val pipeInsLow: Int = selInsHigh + 1//TODO
  val pipeInsHigh: Int = log2Ceil(maxDelay) - 1 + pipeInsLow//TODO
  for (outPort <- 0 until numModuleOutput;
       subNet <- 0 until numDecomp;
       operand <- 0 until 2) {
    val leastWidth = {
      if (maxDelayPipeLen(outPort)(subNet)(operand) < 2) 1
      else log2Ceil(maxDelayPipeLen(outPort)(subNet)(operand))
    }
    pipeLenReg(
      numModuleOutput * numDecomp * operand +
        numModuleOutput * subNet +
        outPort) =
      RegInit(0.U(leastWidth.W))
    when(io.cfg_mode) {
      pipeLenReg(numModuleOutput * numDecomp * operand +
        numModuleOutput * subNet +
        outPort) :=
        io.input_ports(1).bits(pipeInsHigh, pipeInsLow)
    } //TODO: Currently from inport(1)
    //TODO: How to update the register is not defined yet (Instructions related)
  }

  // Opcode Register definition
  val opcodeReg = new Array[UInt](numModuleOutput * numDecomp)
  val opcodeRegInsLow: Int = pipeInsHigh + 1
  val opcodeRegInsHigh: Int = log2Ceil(isa.numISA) - 1 + opcodeRegInsLow
  for (outPort <- 0 until numModuleOutput; subNet <- 0 until numDecomp) {
    opcodeReg(numModuleOutput * subNet + outPort) =
      RegInit(0.U(log2Ceil(isa.numISA).W))
    when(io.cfg_mode) {
      opcodeReg(numModuleOutput * subNet + outPort) :=
        io.input_ports(1).bits(pipeInsHigh, pipeInsLow)
    } //TODO: Currently from inport(1)
    //TODO: How to update the register is not defined yet (Instructions related)
  }


  // Output Register definition
  val OutputBitsReg = RegInit(VecInit(Seq.fill(numModuleOutput * numDecomp)(0.U(decompDataWidth.W))))
  val OutputValidReg = RegInit(VecInit(Seq.fill(numModuleOutput * numDecomp)(false.B)))

  // Output Register <-> output_port
  for (subNet <- 0 until numDecomp; outPort <- 0 until numModuleOutput) {
    io.output_ports(numModuleOutput * subNet + outPort).valid <>
      OutputValidReg(numModuleOutput * subNet + outPort)
    io.output_ports(numModuleOutput * subNet + outPort).bits <>
      OutputBitsReg(numModuleOutput * subNet + outPort)
  }

  //Delay Pipe Definition
  val delayPipes = new Array[DelayPipe](numModuleOutput * numDecomp * 2)

  //ALU Definition
  val ALUes = new Array[ALU](numModuleOutput * numDecomp)
  for(subNet <- 0 until numDecomp;outPort <- 0 until numModuleOutput){
    ALUes(numModuleOutput * subNet + outPort) = Module(new ALU(Instructions(outPort)(subNet), decompDataWidth))
  }

  for (subNet <- 0 until numDecomp; operand <- 0 until 2) {

    val MuxNBitsMatrix = new Array[Array[(chisel3.core.UInt, chisel3.core.UInt)]](numModuleOutput)
    val MuxNValidMatrix = new Array[Array[(chisel3.core.UInt, chisel3.core.Bool)]](numModuleOutput)

    for (outPort <- 0 until numModuleOutput) {
      val numMuxIn: Int = muxDirMatrix(outPort)(subNet)(operand).count(p => p)

      MuxNBitsMatrix(outPort) = new Array[(UInt, UInt)](numMuxIn)
      MuxNValidMatrix(outPort) = new Array[(UInt, Bool)](numMuxIn)

      val leastWidth = {
        if (maxDelayPipeLen(outPort)(subNet)(operand) < 2) 1
        else log2Ceil(maxDelayPipeLen(outPort)(subNet)(operand))
      }
      require(leastWidth > 0)
      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort) =
        Module(new DelayPipe(leastWidth, decompDataWidth))

      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort).io.delayLen :=
        pipeLenReg(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort)
      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort).io.cfg_mode := io.cfg_mode


      val currInDir = muxDirMatrix(outPort)(subNet)(operand).zipWithIndex.filter(_._1 == true).map(_._2)

      for (selSig <- 0 until numMuxIn) {
        require(MuxNBitsMatrix(outPort).length == numMuxIn)

        MuxNBitsMatrix(outPort)(selSig) = selSig.U ->
          io.input_ports(numModuleInput * subNet + currInDir(selSig)).bits(decompDataWidth  - 1, 0)
        MuxNValidMatrix(outPort)(selSig) = selSig.U ->
          io.input_ports(numModuleInput * subNet + currInDir(selSig)).valid

      }

      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort).io.input_ports.bits :=
        MuxLookup(SelReg(numModuleOutput * subNet + outPort), 0.U, MuxNBitsMatrix(outPort))
      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort).io.input_ports.valid :=
        MuxLookup(SelReg(numModuleOutput * subNet + outPort), false.B, MuxNValidMatrix(outPort))


      ALUes(numModuleOutput * subNet + outPort).io.opcode := opcodeReg(numModuleOutput * subNet + outPort)

      if(operand == 0){
        ALUes(numModuleOutput * subNet + outPort).io.operand1 <>
          delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort).io.output_ports
      }else if(operand == 1){
        ALUes(numModuleOutput * subNet + outPort).io.operand2 <>
          delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort).io.output_ports
      }

      ALUes(numModuleOutput * subNet + outPort).io.output_ports.ready :=
        io.output_ports(numModuleOutput * subNet + outPort).ready

      when(io.output_ports(numModuleOutput * subNet + outPort).ready) {
        OutputBitsReg(numModuleOutput * subNet + outPort) :=
          ALUes(numModuleOutput * subNet + outPort).io.output_ports.bits
        OutputValidReg(numModuleOutput * subNet + outPort) :=
          ALUes(numModuleOutput * subNet + outPort).io.output_ports.valid
      }
    }
  }

  for (inPort <- 0 until numModuleInput;subNet <- 0 until numDecomp){
    var readySum = true.B
    for(outPort <- 0 until numModuleOutput;operand <- 0 until 2) {
      if (muxDirMatrix(outPort)(subNet)(operand)(inPort)){
        readySum =
          readySum & delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * subNet + outPort).io.input_ports.ready
      }
    }
    io.input_ports(numModuleInput * subNet + inPort).ready := readySum
  }

  // Debug

  for (outPortIndex <- 0 until numOutput) {
    printf(p"OutputPort-$outPortIndex-bits-${-io.output_ports(outPortIndex).bits}\n")
  }

}

// Instantiate

object DelayPipeDriver extends App {chisel3.Driver.execute(args, () => new DelayPipe(4,32))}
object AluDriver extends App {chisel3.Driver.execute(args, () => new ALU(Array(0,1,2),32))}
object FuDriver extends App {chisel3.Driver.execute(args, () =>
  new Function_Unit(4,4,
    Array((1,0),(0,1),(-1,0),(0,-1)),
    Array((1,0),(0,1),(-1,0),(0,-1)),
    4,
    Array(
      Array(Array(0,1,2),Array(0,1,2),Array(0,1,2),Array(0,1,2)),
      Array(Array(1,2),Array(0,1,2),Array(0,1,2),Array(0)),
      Array(Array(0,1,2),Array(0,1,2),Array(0,2),Array(0,1,2)),
      Array(Array(0,1),Array(0,2),Array(0,1,2),Array(0,2))
    ),
    Array(
      Array(Array(4,3),Array(2,6),Array(1,7),Array(3,3)),
      Array(Array(1,7),Array(1,2),Array(0,5),Array(0,0)),
      Array(Array(1,2),Array(0,5),Array(2,6),Array(1,7)),
      Array(Array(0,5),Array(2,6),Array(1,7),Array(1,2))
    ),
    Array(
      Array(
        Array(Array(false,true,false,true),Array(true, false, true, false)),
        Array(Array(false,true,false,true),Array(false,true,false,true)),
        Array(Array(true, false, true, false),Array(true, false, true, false)),
        Array(Array(true,false,false,true),Array(true, false, true, false))),
      Array(
        Array(Array(false,true,false,true),Array(true, false, true, false)),
        Array(Array(false,true,false,true),Array(false,true,false,true)),
        Array(Array(true, false, true, false),Array(true, false, true, false)),
        Array(Array(true,false,false,true),Array(true, false, true, false))),
      Array(
        Array(Array(false,true,false,true),Array(false,true,false,true)),
        Array(Array(true, false, true, false),Array(true, false, true, false)),
        Array(Array(true, false, true, true),Array(true, false, true, false)),
        Array(Array(true,true,false,true),Array(true, false, true, false))),
      Array(
        Array(Array(true, false, true, false),Array(true, false, true, false)),
        Array(Array(true, false, true, false),Array(true, false, true, false)),
        Array(Array(true, false, true, false),Array(true, true, true, false)),
        Array(Array(true,false,false,true),Array(true, false, true, false)))
    ),
    1,2
  )
)}