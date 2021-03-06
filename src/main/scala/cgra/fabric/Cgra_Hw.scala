package cgra.fabric

import cgra.IO.{ReqAckConf_if, mmio_if}
import cgra.IR.IRpreprocessor._
import cgra.config.system_var
import cgra.config.system_util._
import cgra.fabric.Trig_PE_component.common.mmio.unused_host_interface
import cgra.fabric.Trig_PE_component.tia_parameters.fixed_parameters.{TIA_MMIO_DATA_WIDTH, TIA_MMIO_INDEX_WIDTH}
import chisel3._
import cgra.fabric.common.interconnect.Decomp_Adapter._
import scala.collection.mutable
import scala.collection.mutable._
import scala.xml.Elem

class Cgra_Hw(name_p:(String,mutable.Map[String,Any])) extends Module
  with Has_IO
  with Reconfigurable {
  // ------ System Parameter ------
  val cgra = name_p._2
  set_system(cgra)
  val data_word_width : Int = cgra("data_word_width").asInstanceOf[Int]
  private val input_ports = cgra("input_ports").asInstanceOf[List[String]]
  private val output_ports = cgra("output_ports").asInstanceOf[List[String]]
  private val num_input : Int = input_ports.length
  private val num_output : Int = output_ports.length
  val decomposer : Int = 1
  val protocol : String = "DataValidReadyConfig"
  private val AllModules : Map[String,Any] = Map[String,Any]()
  private val topology:List[String] = cgra("links").asInstanceOf[List[String]]

  // ------ Input Output ------
  val io = IO(new Bundle{
    val in = Flipped(Vec(num_input,ReqAckConf_if(data_word_width)))
    val out = Vec(num_output,ReqAckConf_if(data_word_width))
    val hostinterface = mmio_if(TIA_MMIO_INDEX_WIDTH,TIA_MMIO_DATA_WIDTH)
    val enable = Input(Bool())
    val execute = Input(Bool())
  })
  // ------ Calculate Configuration ------
  // Eliminate Default and Add Module ID
  private val vector_ports = try{cgra("vector_ports").asInstanceOf[mutable.Map[String,Any]]
    .filter(p => {!p._1.startsWith("default")})}catch{case _:Throwable => null}
  private val routers = try{cgra("routers").asInstanceOf[mutable.Map[String,Any]]
    .filter(p => {!p._1.startsWith("default")})}catch{case _:Throwable => null}
  private val dedicated_pes = try{cgra("dedicated_pes").asInstanceOf[mutable.Map[String,Any]]
    .filter(p => {!p._1.startsWith("default")})}catch{case _:Throwable => null}
  private val shared_pes = try{cgra("shared_pes").asInstanceOf[mutable.Map[String,Any]]
    .filter(p => {!p._1.startsWith("default")})}catch{case _:Throwable => null}

  // Calculate Number Of Module
  val num_router = if(routers != null) routers.size else 0
  val num_pe = if(dedicated_pes != null) dedicated_pes.size else 0
  val num_trig_pe = if(shared_pes != null) shared_pes.size else 0
  set_module_id_num(num_router + num_pe + num_trig_pe)

  /*
      Hardware Generation
   */
  // ------ Vector Ports ------
  var vector_ports_hw : mutable.Map[String,VectorPort_Hw] = mutable.Map[String,VectorPort_Hw]()
  if(vector_ports != null){
    vector_ports_hw = vector_ports map (iv=> iv._1 -> Module(new VectorPort_Hw(iv)))
    AllModules ++= vector_ports_hw
  }

  // ------ Routers ------
  var routers_hw : mutable.Map[String,Router_Hw] =mutable.Map[String,Router_Hw]()
  if(routers != null){
    routers_hw = routers map (r =>r._1 -> Module(new Router_Hw(r)))
    AllModules ++= routers_hw
  }



  // ------ Dedicated PE ------
  var dedicated_pes_hw : mutable.Map[String,Dedicated_PE_Hw] = mutable.Map[String,Dedicated_PE_Hw]()
  if(dedicated_pes != null){
    dedicated_pes_hw = dedicated_pes map (dp => dp._1 -> Module(new Dedicated_PE_Hw(dp)))
    AllModules ++= dedicated_pes_hw
  }

  // ------ Shared PE ------
  var shared_pes_hw : Map[String,Trig_PE_Hw] = mutable.Map[String,Trig_PE_Hw]()
  if(shared_pes != null){
    shared_pes_hw = shared_pes map (sp => sp._1 -> Module(new Trig_PE_Hw(sp)))
    shared_pes_hw foreach (s=>{
      val s_pe = s._2
      s_pe.io.hostInterface <> io.hostinterface
      s_pe.io.enable <> io.enable
      s_pe.io.execute <> io.execute
    })
    AllModules ++= shared_pes_hw
  }else{
    val unused = Module(new unused_host_interface).io
    io.hostinterface <> unused
  }


  // ------ Topology ------
  connect_all(AllModules,topology)

  // ------ Output Config ------
  scala.xml.XML.save(cgra("config_filename")toString,config2XML,"UTF-8",false,null)

  // ------ Util ------
  def get_port (io_t:String,name:String) = {
    io_t match {
      case "in" => VecInit(io.in(input_ports.indexOf(name)))
      case "out" => VecInit(io.out(output_ports.indexOf(name)))
    }
  }
  def get_port_protocol(io_t:String,name:String) : String = {
    protocol
  }
  def get_module (name: String) = {
    val mod = name match {
      case "this" => this
      case _ => AllModules(name)
    }
    mod.asInstanceOf[Has_IO]
  }
  def connect_all(mods:Map[String,Any],cons:List[String]):Unit={
    cons.foreach(cc=>{
      val c = parse_conn(cc)
      // Get Hardware from Source
      val source_module_name = c.source.module
      val source_port_name = c.source.port
      val source_mod = get_module(source_module_name)
      // Get Hardware from Sink
      val sink_module_name = c.sink.module
      val sink_port_name = c.sink.port
      val sink_mod = get_module(sink_module_name)
      // Print Current Connection
      println(source_module_name + "-port-" + source_port_name + " --> " + sink_module_name + "-port-" + sink_port_name)
      // Get Source Port Hardware
      val source_port : Vec[ReqAckConf_if] = if (source_mod == this)
        source_mod.get_port("in",source_port_name)
      else if (sink_mod == this)
        source_mod.get_port("out",source_port_name)
      else
        source_mod.get_port("out",source_port_name)
      // Get Sink Port Hardware
      val sink_port : Vec[ReqAckConf_if] = if (source_mod == this)
        sink_mod.get_port("in",sink_port_name)
      else if (sink_mod == this)
        sink_mod.get_port("out",sink_port_name)
      else
        sink_mod.get_port("in",sink_port_name)

      // Get Source Parameter
      val source_data_word_width : Int = source_mod.data_word_width
      val source_decomposer : Int = source_mod.decomposer
      val source_protocol : String = source_mod.get_port_protocol("out",source_port_name)
      val sink_data_word_width = sink_mod.data_word_width
      val sink_decomposer : Int = sink_mod.decomposer
      val sink_protocol : String = sink_mod.get_port_protocol("in",sink_port_name)
      // Requirement Check
      require(source_data_word_width == sink_data_word_width,"" +
        "source and sink should have same word width")
      // Real Connect
      if(source_decomposer == sink_decomposer){
        connect(source_port,sink_port,source_protocol,sink_protocol,source_decomposer)
      }else{
        connect(source_port, sink_port,
          source_data_word_width, sink_data_word_width,
          source_decomposer, sink_decomposer,
          source_protocol, sink_protocol)
      }
    })
  }
  def config2XML : Elem = {
    <CGRA>
      <Routers>{routers_hw.map(r=>r._2.asInstanceOf[Reconfigurable].config2XML)}</Routers>
      {if(dedicated_pes_hw != null) <Dedicated_PEs>{dedicated_pes_hw.map(r=>r._2.asInstanceOf[Reconfigurable].config2XML)}</Dedicated_PEs>}
      {if(shared_pes_hw != null) <Shared_PEs>{shared_pes_hw.map(r=>r._2.asInstanceOf[Reconfigurable].config2XML)}</Shared_PEs>}
    </CGRA>
  }
}