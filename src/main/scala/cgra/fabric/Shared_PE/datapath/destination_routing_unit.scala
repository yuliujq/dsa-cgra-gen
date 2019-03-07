package cgra.fabric.Shared_PE.datapath

import cgra.entity.Entity
import cgra.fabric.Shared_PE.parameters.derived_parameters
import chisel3._
import chisel3.util._

class destination_routing_unit(p:Entity) extends Module with derived_parameters{
  parameter_update(p)
  val io = IO(new Bundle{
    val datapath_result = Input(UInt(TIA_WORD_WIDTH.W))
    val dt = Input(UInt(TIA_DT_WIDTH.W))
    val di = Input(UInt(TIA_DI_WIDTH.W))
    val oci = Input(UInt(TIA_OCI_WIDTH.W))
    val register_write_enable = Output(Bool())
    val register_write_index = Output(UInt(TIA_REGISTER_INDEX_WIDTH.W))
    val register_write_data = Output(UInt(TIA_WORD_WIDTH.W))
    val output_channel_data = Output(Vec(TIA_NUM_OUTPUT_CHANNELS,UInt(TIA_WORD_WIDTH.W)))
  })

  for (i <- 0 until TIA_NUM_OUTPUT_CHANNELS){
    when(io.oci(i)){
      io.output_channel_data(i) := io.datapath_result
    }.otherwise{
      io.output_channel_data(i) := 0.U
    }
  }
  when (io.dt === TIA_DESTINATION_TYPE_REGISTER.U){
    io.register_write_data := io.datapath_result
    io.register_write_index := io.di
    io.register_write_enable := true.B
  }.otherwise{
    io.register_write_data := 0.U
    io.register_write_index := 0.U
    io.register_write_enable := false.B
  }
}
