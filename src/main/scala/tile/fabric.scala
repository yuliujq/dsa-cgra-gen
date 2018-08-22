package cgraexplorationframework.simplechip.tile

import cgraexplorationframework.simplechip.config._
import chisel3._
import chisel3.util._

trait HasFabricParams {
  val CGRAdataWidth = 64
}

trait HasFabricModuleParams extends HasFabricParams
{
  val datawidthModule       : Int
  val numModuleInput        : Int
  val numModuleOutput       : Int
  val inputMoudleDirection  : Array[Double]
  val outputModuleDirection : Array[Double]
  val numDecomp             : Int

  require(isPow2(CGRAdataWidth/numDecomp))
}


abstract class FabricModule  extends Module
  with HasFabricModuleParams{
  lazy val io = IO(new Bundle{
    val input_ports = Vec(numModuleInput,Flipped(DecoupledIO(UInt(datawidthModule.W))))
    val output_ports = Vec(numModuleOutput,DecoupledIO(UInt(datawidthModule.W)))
  })
}

/*
trait HasFabricModuleIO extends FabricModuleBundle
  with HasFabricModuleParams{

  implicit val p: Parameters
  val io = new FabricModuleBundle()(p) {
    val input_ports = Flipped(DecoupledIO(UInt(datawidthModule.W)))
  }
}
*/

/*
trait FabricModuleIO extends Bundle
  with HasFabricModuleParams{
  val io = new Bundle{
    val input_ports = Vec(numModuleInput,Flipped(DecoupledIO(UInt(datawidthModule.W))))
    val output_ports = Vec(numModuleOutput,DecoupledIO(UInt(datawidthModule.W)))
  }
}
*/
