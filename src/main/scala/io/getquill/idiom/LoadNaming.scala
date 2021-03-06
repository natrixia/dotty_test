package io.getquill.idiom

import scala.util.Try

import scala.quoted.{Type => TType, _}
import scala.quoted.matching._
import io.getquill.NamingStrategy
import io.getquill.util.CollectTry
import io.getquill.util.LoadObject
import io.getquill.CompositeNamingStrategy

object LoadNaming {

  def static[T](tpe: TType[T])(given qctx: QuoteContext): Try[NamingStrategy] = {
    import qctx.tasty.{Try => _, _, given}

    def `endWith$`(str: String) =
      if (str.endsWith("$")) str else str + "$"
    
    def loadFromTastyType[T](tpe: Type): Try[T] =
      Try {
        val className = `endWith$`(tpe.classSymbol.get.fullName)
        val cls = Class.forName(className)
        val field = cls.getField("MODULE$")
        field.get(cls).asInstanceOf[T]
      }

    CollectTry {
      strategies(tpe).map(loadFromTastyType[NamingStrategy](_))
    }.map(NamingStrategy(_))
  }

  private def strategies[T](tpe: TType[T])(given qctx: QuoteContext) = {
    import qctx.tasty.{_, given}
    val treeTpe = '[$tpe].unseal.tpe
    treeTpe <:< '[CompositeNamingStrategy].unseal.tpe match {
      case true =>
        treeTpe match {
          case AppliedType(_, types) => 
            types
              .filter(_.isInstanceOf[Type]).map(_.asInstanceOf[Type])
              .filterNot(_ =:= '[NamingStrategy].unseal.tpe)
              .filterNot(_ =:= '[Nothing].unseal.tpe)
        }
      case false =>
        List(treeTpe)
    }
  }

  inline def mac[T](t: T): String = ${ macImpl[T]('t) }
  def macImpl[T](t: Expr[T])(given qctx: QuoteContext, tpe: TType[T]): Expr[String] = {
    import qctx.tasty.{_, given}
    val loadedStrategies = strategies(tpe)
    println( loadedStrategies )
    Expr(loadedStrategies.toString) // maybe list of string?
  }
}


inline def macLoadNamingStrategy[T](t: T): String = ${ macLoadNamingStrategyImpl[T]('t) }
def macLoadNamingStrategyImpl[T](t: Expr[T])(given qctx: QuoteContext, tpe: TType[T]): Expr[String] = {
  import qctx.tasty.{_, given}
  val loadedStrategies = LoadNaming.static(tpe)
  println( loadedStrategies )
  Expr(loadedStrategies.toString) // maybe list of string?
}


