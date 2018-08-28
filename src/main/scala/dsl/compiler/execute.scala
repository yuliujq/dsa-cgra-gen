// See README.md for license details.

package dsl.compiler

import dsl.IR.CgraModel
import dsl.lex.Item


trait execute extends Env
  with ClassInitializer
  {
  def execClassInstantiate(Enviro:Env,
                           line:ClassInstantiate,
                           model:CgraModel):(Env,CgraModel)=
  {



    val instanClass:Item = line.ClassInstantiateTarget

    val instanClassName :String = instanClass.itemName

    val cgraClass:CgraClass = line.ClassInstantiateFrom
    val classType:String = cgraClass.ClassType.itemName
    val classMember:List[Any] = cgraClass.ClassMembers

    //TODO: Currently the encode and index is not used
    /*
    val instanClassEncode : Int = instanClass.itemEncode
    val instanClassIndex : Int = instanClass.itemIndex
    val classTypeEncode:Int = cgraClass.ClassType.itemEncode
    val classTypeIndex:Int = cgraClass.ClassType.itemIndex
    */

    if(Enviro.VariableList.exists(i=>{
      i.Name == instanClassName
    })){
      throw new Exception(instanClassName + " has " +
        "already been instantiated")
    }

    val envVariable:Enviro.EnvVariable = new Enviro.EnvVariable{
      Name = instanClassName
      Type = classType
      Entity = classInitializer(classType,classMember)
    }

    Enviro.VariableList = Enviro.VariableList :+ envVariable

    val foo = line

    (Enviro,model)
  }

  def execAssign(Enviro:Env,
                 line:Assign,
                 model:CgraModel):(Env,CgraModel)={

    val assignTarget = line.AssignTarget
    val assignTargetName = assignTarget.itemName
    val assignFrom = line.AssignFrom

    if(Enviro.VariableList.exists(i=>{
      i.Name == assignTarget.itemName
    })){

    }else{
      throw new Exception(assignTargetName + " is not existed")
    }

    (Enviro,model)
  }

  def execConnection(Enviro:Env,line:Connection,model:CgraModel):(Env,CgraModel)={

    (Enviro,model)
  }
}
