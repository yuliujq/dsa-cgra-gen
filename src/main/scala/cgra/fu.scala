package cgraexplorationframework.simplechip.cgra

import chisel3._
import chisel3.util._
import cgraexplorationframework.simplechip.tile._

class FU(
          numInput        : Int,
          numOutput       : Int,
          inputDirection  : Array[Double],
          outputDirection : Array[Double],
          deComp          : Int,
          Instructions    : Array[Array[Array[Int]]], //Instructions(outPort)(decomp) : Array of Instructions Set
          maxDelayPipeLen : Array[Array[Array[Int]]], //maxDelayPipeLen(outPort)(decomp)(operand)
          muxDirMatrix    : Array[Array[Array[Array[Boolean]]]] //muxDirMatrix(outPort)(decomp)(operand)(inPut)
        ) extends FabricModule {
  //Override value
  override val datawidthModule: Int = fabricDataWidth
  override lazy val numModuleInput: Int = numInput
  override lazy val numModuleOutput: Int = numOutput
  override lazy val inputMoudleDirection: Array[Double] = inputDirection
  override lazy val outputModuleDirection: Array[Double] = outputDirection
  override lazy val numDecomp: Int = deComp

  val maxDelay: Int = maxDelayPipeLen.map {
    _.map {
      _.max
    }.max
  }.max

  // Requirement check
  require(numModuleOutput == muxDirMatrix.length)
  for (decomp <- 0 until numDecomp) {
    for (outPort <- 0 until this.numModuleOutput; operand <- 0 until 2) {
      require(numModuleInput == muxDirMatrix(outPort)(decomp)(operand).length, "Mux select Matrix size mismatch")
      require(muxDirMatrix(outPort)(decomp)(operand).exists(p => p), s"each output direction need to have one input,Output ${outPort + 1} Sec ${decomp + 1}")
    }
    for (inPort <- 0 until this.numModuleInput; operand <- 0 until 2) {
      require(numModuleOutput == muxDirMatrix.map {
        _ (decomp)(operand)(inPort)
      }.length, "Mux select Matrix size mismatch")
      require(muxDirMatrix.flatMap {
        _ (decomp)
      }.map {
        _ (inPort)
      }.exists(p => p), s"each input direction need to have one output,Input ${inPort + 1} Sec ${decomp + 1}")
    }
  }

  // Select Register definition
  val SelReg = new Array[UInt](numModuleOutput * numDecomp * 2)
  val selInsHigh: Int = log2Ceil(numModuleOutput) - 1;
  val selInsLow = 0
  for (outPort <- 0 until numModuleOutput; decomp <- 0 until numDecomp; operand <- 0 until 2) {
    SelReg(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort) =
      RegInit(0.U(log2Ceil(numModuleOutput).W))
    when(io.cfg_mode) {
      SelReg(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort) :=
        io.input_ports(1).bits(selInsHigh, selInsLow)
    } //TODO: Currently from inport(1)
    //TODO: How to update the register is not defined yet (Instructions related)
  }

  // Delay Pipe Len Register definition
  val pipeLenReg = new Array[UInt](numModuleOutput * numDecomp * 2)
  val pipeInsLow: Int = selInsHigh + 1;
  val pipeInsHigh: Int = log2Ceil(maxDelay) - 1 + pipeInsLow
  for (outPort <- 0 until numModuleOutput; decomp <- 0 until numDecomp; operand <- 0 until 2) {
    var leastWidth = {
      if (maxDelayPipeLen(outPort)(decomp)(operand) < 2) 1
      else log2Ceil(maxDelayPipeLen(outPort)(decomp)(operand))
    }
    pipeLenReg(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort) =
      RegInit(0.U(leastWidth.W))
    when(io.cfg_mode) {
      pipeLenReg(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort) :=
        io.input_ports(1).bits(pipeInsHigh, pipeInsLow)
    } //TODO: Currently from inport(1)
    //TODO: How to update the register is not defined yet (Instructions related)
  }

  // Opcode Register definition
  val opcodeReg = new Array[UInt](numModuleOutput * numDecomp)
  val opcodeRegInsLow: Int = pipeInsHigh + 1;
  val opcodeRegInsHigh: Int = log2Ceil(isa.maxNumISA) - 1 + opcodeRegInsLow
  for (outPort <- 0 until numModuleOutput; decomp <- 0 until numDecomp) {
    opcodeReg(numModuleOutput * decomp + outPort) =
      RegInit(0.U(log2Ceil(isa.maxNumISA).W))
    when(io.cfg_mode) {
      opcodeReg(numModuleOutput * decomp + outPort) :=
        io.input_ports(1).bits(pipeInsHigh, pipeInsLow)
    } //TODO: Currently from inport(1)
    //TODO: How to update the register is not defined yet (Instructions related)
  }


  // Output Register definition
  val OutputBitsReg = RegInit(VecInit(Seq.fill(numModuleOutput * numDecomp)(0.U(decompDataWidth.W))))
  val OutputValidReg = RegInit(VecInit(Seq.fill(numModuleOutput * numDecomp)(false.B)))

  // Output Register <-> output_port
  for (decomp <- 0 until numDecomp; outPort <- 0 until numModuleOutput) {
    io.output_ports(numModuleOutput * decomp + outPort).valid <>
      OutputValidReg(numModuleOutput * decomp + outPort)
    io.output_ports(numModuleOutput * decomp + outPort).bits <>
      OutputBitsReg(numModuleOutput * decomp + outPort)
  }

  //Delay Pipe Definition
  val delayPipes = new Array[DelayPipe](numModuleOutput * numDecomp * 2)

  //ALU Definition
  val ALUes = new Array[ALU](numModuleOutput * numDecomp)
  for(decomp <- 0 until numDecomp;outPort <- 0 until numModuleOutput){
    ALUes(numModuleOutput * decomp + outPort) = Module(new ALU(Instructions(outPort)(decomp), decompDataWidth))
  }

  for (decomp <- 0 until numDecomp; operand <- 0 until 2) {

    var MuxNBitsMatrix = new Array[Array[(chisel3.core.UInt, chisel3.core.UInt)]](numModuleOutput)
    var MuxNValidMatrix = new Array[Array[(chisel3.core.UInt, chisel3.core.Bool)]](numModuleOutput)

    for (outPort <- 0 until numModuleOutput) {
      var numMuxIn: Int = muxDirMatrix(outPort)(decomp)(operand).count(p => p)

      MuxNBitsMatrix(outPort) = new Array[(UInt, UInt)](numMuxIn)
      MuxNValidMatrix(outPort) = new Array[(UInt, Bool)](numMuxIn)

      var leastWidth = {
        if (maxDelayPipeLen(outPort)(decomp)(operand) < 2) 1
        else log2Ceil(maxDelayPipeLen(outPort)(decomp)(operand))
      }
      require(leastWidth > 0)
      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort) =
        Module(new DelayPipe(leastWidth, decompDataWidth))

      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort).io.delayLen :=
        pipeLenReg(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort)
      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort).io.cfg_mode := io.cfg_mode


      var currInDir = muxDirMatrix(outPort)(decomp)(operand).zipWithIndex.filter(_._1 == true).map(_._2)

      for (selSig <- 0 until numMuxIn) {
        require(MuxNBitsMatrix(outPort).length == numMuxIn)

        MuxNBitsMatrix(outPort)(selSig) = selSig.U ->
          io.input_ports(numModuleInput * decomp + currInDir(selSig)).bits(decompDataWidth * (decomp + 1) - 1, decompDataWidth * decomp)
        MuxNValidMatrix(outPort)(selSig) = selSig.U ->
          io.input_ports(numModuleInput * decomp + currInDir(selSig)).valid

      }

      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort).io.input_ports.bits :=
        MuxLookup(SelReg(numModuleOutput * decomp + outPort), 0.U, MuxNBitsMatrix(outPort))
      delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort).io.input_ports.valid :=
        MuxLookup(SelReg(numModuleOutput * decomp + outPort), false.B, MuxNValidMatrix(outPort))


      ALUes(numModuleOutput * decomp + outPort).io.opcode := opcodeReg(numModuleOutput * decomp + outPort)


      ALUes(numModuleOutput * decomp + outPort).io.output_ports.ready :=
        io.output_ports(numModuleOutput * decomp + outPort).ready

      when(io.output_ports(numModuleOutput * decomp + outPort).ready) {
        OutputBitsReg(numModuleOutput * decomp + outPort) :=
          ALUes(numModuleOutput * decomp + outPort).io.output_ports.bits
        OutputValidReg(numModuleOutput * decomp + outPort) :=
          ALUes(numModuleOutput * decomp + outPort).io.output_ports.valid
      }

    }
  }

  for (outPort <- 0 until numModuleOutput;decomp <- 0 until numDecomp){
    ALUes(numModuleOutput * decomp + outPort).io.operand1.bits :=
      delayPipes(numModuleOutput * numDecomp * 0 + numModuleOutput * decomp + outPort).io.output_ports.bits
    ALUes(numModuleOutput * decomp + outPort).io.operand1.valid :=
      delayPipes(numModuleOutput * numDecomp * 0 + numModuleOutput * decomp + outPort).io.output_ports.valid
    delayPipes(numModuleOutput * numDecomp * 0 + numModuleOutput * decomp + outPort).io.output_ports.ready :=
      ALUes(numModuleOutput * decomp + outPort).io.operand1.ready

    ALUes(numModuleOutput * decomp + outPort).io.operand2.bits :=
      delayPipes(numModuleOutput * numDecomp * 1 + numModuleOutput * decomp + outPort).io.output_ports.bits
    ALUes(numModuleOutput * decomp + outPort).io.operand2.valid :=
      delayPipes(numModuleOutput * numDecomp * 1 + numModuleOutput * decomp + outPort).io.output_ports.valid
    delayPipes(numModuleOutput * numDecomp * 1 + numModuleOutput * decomp + outPort).io.output_ports.ready :=
      ALUes(numModuleOutput * decomp + outPort).io.operand2.ready
  }

  for (inPort <- 0 until numModuleInput;decomp <- 0 until numDecomp){
    var readySum = true.B
    for(outPort <- 0 until numModuleOutput;operand <- 0 until 2) {
      if (muxDirMatrix(outPort)(decomp)(operand)(inPort)){
        readySum =
          readySum & delayPipes(numModuleOutput * numDecomp * operand + numModuleOutput * decomp + outPort).io.input_ports.ready
      }
    }
    io.input_ports(numModuleInput * decomp + inPort).ready := readySum
  }
}

class ALU (InstructionList : Array[Int],aluDataWidth : Int) extends Module {
  val io = IO(new Bundle{
    val operand1 = Flipped(DecoupledIO(UInt(aluDataWidth.W)))
    val operand2 = Flipped(DecoupledIO(UInt(aluDataWidth.W)))
    val opcode = Input(UInt(log2Ceil(InstructionList.length).W))
    val output_ports = DecoupledIO(UInt(aluDataWidth.W))
  }
  )
  val result = Wire(UInt(aluDataWidth.W))
  io.operand1.ready <> io.output_ports.ready
  io.operand2.ready <> io.output_ports.ready
  io.output_ports.bits <> result
  io.output_ports.valid <> false.B
  result := 0.U

  if (InstructionList.contains(isa.OR)){
    when(io.opcode === isa.OR.U){
      result := (io.operand1.bits | io.operand2.bits)
    }
  }
  if (InstructionList.contains(isa.AND)){
    when(io.opcode === isa.AND.U){
      result := (io.operand1.bits & io.operand2.bits)
    }
  }
  if (InstructionList.contains(isa.XOR)){
    when(io.opcode === isa.XOR.U){
      result := (io.operand1.bits ^ io.operand2.bits)
    }
  }

  when(io.operand1.valid && io.operand2.valid){
    io.output_ports.valid <> true.B
  }
}

class DelayPipe (maxLength:Int,pipeDataWidth:Int) extends Module {
  val io = IO(new Bundle{
    val cfg_mode = Input(Bool())
    val delayLen = Input(UInt(log2Ceil(maxLength + 1).W))
    val input_ports = Flipped(DecoupledIO(UInt(pipeDataWidth.W)))
    val output_ports = DecoupledIO(UInt(pipeDataWidth.W))
  })

  io.input_ports.ready := io.output_ports.ready

  val FIFO = RegInit(VecInit(Seq.fill(maxLength + 1)(0.U(pipeDataWidth.W))))
  val FIFO_Valid = RegInit(VecInit(Seq.fill(maxLength + 1)(false.B)))

  var leastWidth = 0
  if(maxLength < 2) leastWidth = 2
  else leastWidth = maxLength
  val delay = RegInit(0.U(log2Ceil(leastWidth).W))


  when(io.cfg_mode) {
    // Reconfiguration
    // reconfig delay
    delay := io.delayLen
    // empty the FIFO
    for (i <- 0 until maxLength) {
      FIFO(i) := 0.U
      FIFO_Valid(i) := false.B
    }
  }.otherwise {
    for (i <- 0 until maxLength) {
      when(io.output_ports.ready){
        when((i.U(maxLength.W) === delay)) {
          FIFO(i) := io.input_ports.bits
          FIFO_Valid(i) := io.input_ports.valid
        }.otherwise {
          FIFO(i) := FIFO(i + 1)
          FIFO_Valid(i) := FIFO_Valid(i + 1)
        }
      }
    }
  }

  io.output_ports.bits := FIFO(0)
  io.output_ports.valid := FIFO_Valid(0)

  // Debug
  /*
  printf(p" ------- FIFO start --------\n")
  printf(p"FIFO input = ${io.in_ports} \n")
  printf(p"FIFO output = ${io.out_ports} \n")
  printf(p"--------  FIFO end -------\n")
  */
}

object DelayPipeDriver extends App {chisel3.Driver.execute(args, () => new DelayPipe(6,32))}
object AluDriver extends App {chisel3.Driver.execute(args, () => new ALU(Array(0,1,2),32))}

object FuDriver extends App {chisel3.Driver.execute(args, () =>
  new FU(4,4,Array(0.0,90.0,180.0,270.0),Array(0.0,90.0,180.0,270.0),4,
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
    )
  )
)}