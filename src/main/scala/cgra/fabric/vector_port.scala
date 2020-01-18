package cgra.fabric

import cgra.IO._
import cgra.fabric.common.interconnect.{crossbar, crossbar_flow_control}
import chisel3._
import chisel3.util._
import dsl.IRPrintable

import scala.collection.mutable
import scala.util.Random

class vector_port(prop:mutable.Map[String,Any])
  extends Module with IRPrintable {
  // Assign initial properties
  apply(prop)

  // ------ Reconfigurable Variable------

  // Initialize the properties of switch (hardware)
  private val data_width:Int = getPropByKey("data_width").asInstanceOf[Int]
  private var num_input:Int = getPropByKey("num_input").asInstanceOf[Int]
  private var num_output:Int = getPropByKey("num_output").asInstanceOf[Int]
  private val num_port = num_input max num_output
  private val flow_control : Boolean = getPropByKey("flow_control")
    .asInstanceOf[Boolean]
  private val input_nodes = getPropByKey("input_nodes").asInstanceOf[Seq[Any]]
  private val output_nodes = getPropByKey("output_nodes").asInstanceOf[Seq[Any]]
  if(input_nodes.length < num_input){
    num_input = input_nodes.length
  }
  if(output_nodes.length < num_output){
    num_output = output_nodes.length
  }

  // Derived Parameter
  val config_width = num_port * log2Ceil(num_port)

  // Create the I/O port
  val io = IO(new VecDecoupledIO_conf(
    num_port,num_port,data_width+1,config_width
  ))

  // Create Register to Store the Config bits
  val config_bits = RegEnable(io.config.bits,0.U,io.config.valid)

  prop += "in_data_width" -> Seq.fill(num_port)(data_width + 1)
  prop += "out_data_width"-> Seq.fill(num_port)(data_width + 1)

  // ------ Logic Connections
  if(true){ // vector port need to be flow controlled all the time
    val xbar = Module(new crossbar_flow_control(prop)).io
    if(num_port > 1){
      xbar.config := config_bits
    }else{
      xbar.config := DontCare
    }
    for (idx <- 0 until num_port){
      // Bits & Config
      xbar.ins(idx) <> io.input_ports(idx)
      io.output_ports(idx) <> xbar.outs(idx)
    }
  }else{
    val xbar = Module(new crossbar(prop)).io
    if(num_port > 1){
      xbar.config := config_bits
    }else{
      xbar.config := DontCare
    }
    for (idx <- 0 until num_port){
      // Bits & Config
      xbar.ins(idx) := io.input_ports(idx).bits
      io.output_ports(idx).bits := xbar.outs(idx)(data_width,1)
      // DontCare Flow Control
      io.input_ports(idx).ready := DontCare
      io.output_ports(idx).valid := DontCare
    }
  }



  // Post process
  def postprocess(): Unit = {
    print(this)
  }
}


import cgra.IR.IRreader._

object gen_vp extends App{

  val cgra = readIR(args(0))

  val nodes = cgra("nodes")
    .asInstanceOf[List[mutable.Map[String,Any]]]

  for (node <- nodes){
    if(node("nodeType") == "vector port"){
      chisel3.Driver.execute(args,()=>{
        val module = new vector_port(node)
        println(module)
        module
      })
    }
  }
}