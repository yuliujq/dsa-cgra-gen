package cgra.fabric

import cgra.config._
import chisel3.Module
import config._
/*
class router(implicit p:Parameters,param_id:Router_Key) extends Module{
  val routerParam : RouterParams = p(param_id)
}
*/

trait HasRouter extends Build{

  /*
  def newRouter:Router_Key = newRouter(module_type,module_id,new_subtile_id)

  def newRouter(parent_type: String,parent_id: Int,tile_id:Int):
    Router_Key= {
    val key = Router_Key(module_id,new_subtile_id(tile_id))
    addParameters(new AluParams(key))
    key
  }
  */

}