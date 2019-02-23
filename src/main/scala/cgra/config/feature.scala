package cgra.config

/*  abstract key is not useful for now
case object NameKey extends Field[Module_Type_Params]
case object DatapathKey extends Field[DatapathParams]
case object IOKey extends Field[IOParams]
case object TileKey extends Field[TileParams]
*/


trait Module_Type_Params {
  val module_type : String = null
  def get_module_type = module_type
}

trait DatapathParams {
  var word_width : Int = -1
}

trait IOParams {
  var num_input   : Int = 0
  var num_output  : Int = 0
  var input_word_width_decomposer   : List[Int] = Nil
  var output_word_width_decomposer  : List[Int] = Nil
}

// Below is for those categories who can be instantiated but still have sub class,
// like we can have a tile hardware of PE, but PE still have different types

class TileParams(parent_type: String,
                 parent_id: Int,
                 tile_id:Int) extends DatapathParams
  with IOParams
  with Module_Type_Params{
  var x_location  : Int = -1
  var y_location  : Int = -1
  // Judge
  def isPE = module_type == "PE"
  def isRouter = module_type == "router"
  def isInterfacePort = module_type == "if_port"
  // Returm Information
  def getParent = parent_type
  def getParent_id = parent_id
  def getID = tile_id
  def haveID = tile_id >= 0
  // port operation
  def add_output_port: Int = {num_output += 1;num_output}
  def add_input_port : Int = {num_input += 1;num_input}
  def decrease_output_port : Int = {num_output -=1;num_output}
  def decrease_input_port : Int = {num_input -=1;num_input}
  // location operation
  def move_horizontal(x:Int)=x_location = x
  def move_vertical(y:Int)=y_location = y
  def at(x:Int,y:Int) = {x_location = x;y_location = y}
  // duplicate
}

class PeParams(parent_type: String,parent_id:Int,tile_id:Int)
  extends TileParams(parent_type: String,parent_id:Int,tile_id:Int){
  override val module_type:String = "PE"
  var inst_set : List[Int] = Nil
  val inst_firing : String = ""
  def isDedicated : Boolean = inst_firing == "dedicated"
  def isShared : Boolean = inst_firing == "shared"
}

/* Seems not very useful currently, might be useful when we have the chisel implementation of module
// Now just parameters
trait HasDatapathParameters {
  implicit val p : Parameters
  def datapathParams : DatapathParams = p(DatapathKey)
  val word_width = datapathParams.word_width
}
trait HasIOParameters {
  implicit val p : Parameters
  def ioParams : IOParams = p(IOKey)
  val num_input   : Int = ioParams.num_input
  val num_output  : Int = ioParams.num_output
  val input_word_width_decomposer   : Array[Int] = ioParams.input_word_width_decomposer
  val output_word_width_decomposer  : Array[Int] = ioParams.output_word_width_decomposer
}

trait HasTileParameters extends HasDatapathParameters
  with HasIOParameters{
  val parent_id : Int
  def tileParams : TileParams = p(TileKey)
  val module_type:String = tileParams.module_type
  val x_location:Int = tileParams.x_location
  val y_location:Int = tileParams.y_location
}
*/