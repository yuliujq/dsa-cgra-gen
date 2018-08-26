package dsl

import dsl.syx.CgraLan

import util.Properties
import java.io.File


class cgraModel(modelFileName :String){

  // TODO: Need to add parsering function
  val numCols : Int = 5
  val numRows : Int = 6
  val numFabricInput : Int = 10
  val numFabricOutput : Int = 11

  val GridInfo = new Array[Array[GridModule]](numCols * numRows)



}

abstract class GridModule {
  var name: String = ""
  var existed         : Int = -1
  var numInput        : Int = -1
  var numOutput       : Int = -1
  var inputDirection  : Array[(Int,Int)] = Array((-1,-1))
  var outputDirection : Array[(Int,Int)] = Array((-1,-1))
  var deComp          : Int = -1
}

class GridFUInfo extends GridModule {
  var Instructions    : Array[Array[Array[Int]]] =
    Array(
      Array(Array(-1)),
      Array(Array(-1)),
      Array(Array(-1)),
      Array(Array(-1))
    ) //Instructions(outPort)(subNet) : Array of Instructions Set
  var maxDelayPipeLen : Array[Array[Array[Int]]] =
    Array(
      Array(Array(-1)),
      Array(Array(-1)),
      Array(Array(-1)),
      Array(Array(-1))
    ) //maxDelayPipeLen(outPort)(subNet)(operand)
  var muxDirMatrix    : Array[Array[Array[Array[Boolean]]]]=
    Array(
      Array(
        Array(Array(false))),
      Array(
        Array(Array(false))),
      Array(
        Array(Array(false))),
      Array(
        Array(Array(false)))
    )
}

class GridRouterInfo extends GridModule {
  var muxDirMatrix    : Array[Array[Array[Boolean]]] =
    Array(
      Array(
        Array(false),Array(false),Array(false),Array(false)
      )
    )
}

object CgraParseExpr extends CgraLan {
  def main(args: Array[String]) {

    val original_lines = fileRead(args(0))
    val commentFreeLines = commentDelete(deleteLastSep(original_lines))

    val cgraModel:ParseResult[List[Any]] = parseAll(document,commentFreeLines)

    if(cgraModel.successful){
      println("Successfully parsed")
      println(cgraModel)
    }else{
      println("Parsing fail")
    }
  }

  def fileRead(fileInfo:String): String = {

    var fileName:String = new String(fileInfo.getBytes(),"Unicode")
    println(fileName)

    if(!fileInfo.endsWith(".cgral")){
      fileName  = fileName + ".cgral"
    }
    println(fileName)

    try{
      // relative path
      scala.io.Source.fromFile("model/" + fileName,"GBK") mkString
    }catch{
      // absolute path
      case _:Throwable => try{
        val ProjectAbsPath = new File(".").getAbsolutePath().reverse.substring(1).reverse
        val modelDirPath : String = ProjectAbsPath + "model\\"
        println(modelDirPath + fileName)
        scala.io.Source.fromFile(modelDirPath + fileName,"GBK") mkString
      }catch{
        case x :Throwable => {
          println(x)
          throw new Exception(fileName + " : No such file or directory")}
      }
    }

  }

  def commentDelete(undeleted:String) : String ={
    def paraCommentDelete (paraUnDelete: String): String ={
      val sta:Int = paraUnDelete.indexOf("/*")
      val end:Int = paraUnDelete.indexOf("*/") + 2
      val length :Int=paraUnDelete.length
      if((end<sta)|(sta<0)|(end<0))
        paraUnDelete
      else
        paraCommentDelete(paraUnDelete.substring(0,sta)+
          paraUnDelete.substring(sta,end).replaceAll(".+","")
          +paraUnDelete.substring(end,length))
    }
    def lineCommentDelete (lineUnDelete:String) : String ={
      val sta:Int = lineUnDelete.indexOf("//")
      val end:Int = lineUnDelete.indexOf(Properties.lineSeparator,sta)
      val length : Int = lineUnDelete.length
      if((end<sta)|(sta<0)|(end<0))
        lineUnDelete
      else
        lineCommentDelete(lineUnDelete.substring(0,sta)+lineUnDelete.substring(end,length))
    }

    lineCommentDelete(paraCommentDelete(undeleted))
  }

  def deleteLastSep(undeleted:String) : String =
    undeleted.reverse.replaceFirst(";","").reverse
}