// See README.md for license details.

package dsl.lex

import scala.util.parsing.combinator.JavaTokenParsers

trait CgraLanAssignment extends JavaTokenParsers
  with CgraLanFunction
  with CgraLanCollection
  with CgraLanItems
{
  def assign : Parser[Any] =
    item~ "=" ~
      (function
        |collectable
        |location
        |item) ^^
  {
    case aT~_~aF => new Assign {AssignTarget = aT;AssignFrom = aF}
  }

  class Assign {
    var AssignTarget : Item = _
    var AssignFrom : Any = _
  }
}

