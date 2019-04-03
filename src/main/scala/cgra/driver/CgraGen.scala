package cgra.driver

import cgra.IR.IRinstantiator.instantiateCgra
import cgra.IR.IRmerger.mergeIRwithCgra
import cgra.IR.IRreader.readIR

import scala.collection.mutable

object CgraGen extends App{
  val readFile    : String = args(0) // "/home/sihao/ss-cgra-gen/IR/cgra_3x3_new.yaml"
  val output_dir  : String = args(1) // "verilog-output"
  val cgra : mutable.Map[String,Any] = readIR(readFile)
  instantiateCgra(output_dir,cgra)
  mergeIRwithCgra(readFile,output_dir + "/" + cgra("module_type") + ".v")
}