package real

import dsl._

object softbrain extends App {
  // Keys that differ nodes
  identifier("row_idx","col_idx")

  // Define Default Switch
  val sw_default = new ssnode("switch")
  sw_default(
    "subnet_offset", List(0))(
    "switch_mode", "group-by-port")

  // Define General Function Unit
  val fu_general = new ssnode("function unit")
  fu_general(
    "instructions", List("Add", "Sub", "Mul"))(
    "max_delay_fifo_depth", 4)

  // Define SPU
  val softbrain = new ssfabric
  softbrain("default_data_width", 64)(
    "default_flow_control", false)(
    "default_granularity", 64)(
    "default_max_util", 1)

  // Build Topology
  val switchMesh = softbrain.buildMesh(sw_default, 5,5)

  // Add the function unit
  for(row_idx <- 0 until 4;col_idx <- 0 until 4){
    val temp_node = fu_general.clone
    temp_node("row_idx",row_idx)("col_idx",col_idx)
    softbrain(
      softbrain(row_idx)(col_idx)("switch") --> temp_node
    )(
      softbrain(row_idx+1)(col_idx)("switch") --> temp_node
    )(
      softbrain(row_idx)(col_idx+1)("switch") --> temp_node
    )(
      softbrain(row_idx+1)(col_idx+1)("switch") <-- temp_node
    )
  }

  // Connect the Vector port
  val in_vport = new ssnode("vector port")
  val out_vport = new ssnode("vector port")

  // Connect IO
  softbrain(in_vport |=> softbrain("row_idx","nodeType")(0,"switch"))
  softbrain(out_vport <=| softbrain("row_idx","nodeType")(4,"switch"))

  // Print
  softbrain.printfile("IR/softbrain")
}
