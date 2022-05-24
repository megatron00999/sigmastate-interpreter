package sigmastate.lang.syntax

import fastparse.CharPredicates.{isDigit, isLetter}
import fastparse._
import ScalaWhitespace._
import sigmastate.lang.syntax.Basic._

//noinspection ForwardReference
object Identifiers {
  case class NamedFunction(f: Char => Boolean)
      (implicit name: sourcecode.Name) extends (Char => Boolean){
    def apply(t: Char) = f(t)
    override def toString: String = name.value
  }
  val OpCharNotSlash = NamedFunction(x => isOpChar(x) && x != '/')
  val NotBackTick = NamedFunction(_ != '`')

  def Operator[_:P]: P[Unit] = P(
    !Keywords ~ (!("/*" | "//") ~ (CharsWhile(OpCharNotSlash) | "/")).rep(1)
  )

  def VarId[_:P]: P[Unit] = VarId0(true)

  def VarId0[_:P](dollar: Boolean): P[Unit] = P( !Keywords ~ Lower ~ IdRest(dollar) )
  def PlainId[_:P]: P[Unit] = P( !Keywords ~ Upper ~ IdRest(true) | VarId | Operator ~ (!OpChar | &("/*" | "//")) )
  def PlainIdNoDollar[_:P]: P[Unit] = P( !Keywords ~ Upper ~ IdRest(false) | VarId0(false) | Operator )
  def BacktickId[_:P]: P[Unit] = P( "`" ~ CharsWhile(NotBackTick) ~ "`" )
  def Id[_:P]: P0 = P( BacktickId | PlainId )

  def IdRest[_:P](allowDollar: Boolean): P[Unit] = {

    def IdCharacter =
      if(allowDollar) NamedFunction(c => c == '$' || isLetter(c) || isDigit(c))
      else NamedFunction(c => isLetter(c) || isDigit(c))

    def IdUnderscoreChunk = P( CharsWhile("_".contains(_), min = 0) ~ CharsWhile(IdCharacter) )
    P( IdUnderscoreChunk.rep ~ (CharsWhileIn("_") ~ CharsWhile(isOpChar, min = 0)).? )
  }

  final val alphaKeywords = Seq(
    "case", "else", "false", "function", "if", "match", "return", "then", "true"
  )
  def AlphabetKeywords[_:P]: P[Unit] = P {
    StringIn("case", "else", "false", "function", "if", "match", "return", "then", "true") ~ !Letter
  }

  val symbolKeywords = Seq(
    ":", ";", "=>", "=", "#", "@"
  )
  def SymbolicKeywords[_:P]: P[Unit] = P{
    StringIn(":", ";", "=>", "=", "#", "@") ~ !OpChar
  }

//  val keywords: Seq[String] = alphaKeywords ++ symbolKeywords

  def Keywords[_:P]: P[Unit] = P( AlphabetKeywords | SymbolicKeywords )
}
