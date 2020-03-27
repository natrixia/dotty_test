package macroexample

import scala.quoted._
import scala.quoted.matching._


trait Foo {
  def value: String
}
case class Bar(value: String) extends Foo
case class Baz(value: String) extends Foo

object MacroExample {

  // detectPlus(numberOne + numberInt)

  inline def showTreeMatchLambda(inline expr: (String, String) => Int): Unit = ${ showTreeMatchLambdaImpl('expr) }
  def showTreeMatchLambdaImpl(expr: Expr[(String, String) => Int])(given qctx: QuoteContext): Expr[Unit] = {
    import qctx.tasty.{given, _}
    printer.lnf(expr.unseal.underlyingArgument)
    expr.unseal.underlyingArgument match {
      case Lambda(List(ValDef(argName, _, _), ValDef(argName1, _, _)), body) =>
        println("Arg is: " + argName + " and " + argName1)
        println("Body is: " + body.showExtractors)
    }

    '{ () }
  }

  inline def showTree(inline expr: Any): Unit = ${ showTreeImpl('expr) }
  def showTreeImpl(expr: Expr[Any])(given qctx: QuoteContext): Expr[Unit] = {
    import qctx.tasty.{given, _}
    //println(expr.unseal.underlyingArgument.showExtractors)
    printer.lnf(expr.unseal.underlyingArgument)
    '{ () }
  }

  inline def detectPlus(inline expr: Int): (Int, String) = ${ detectPlusImpl('expr) }
  def detectPlusImpl(expr: Expr[Int])(given qctx: QuoteContext): Expr[(Int, String)] = {
    import qctx.tasty.{given, _}
    println(expr.unseal.underlyingArgument.showExtractors)

    val message = 
      expr.unseal.underlyingArgument.seal match {
        case '{ ($one: Int).+(($two: Int)) } => 
          val oneInner = one.unseal match { case Ident(value) => value }
          val twoInner = two.unseal match { case Ident(value) => value }

          s"(${oneInner}, ${twoInner}, +)"
       
        case _ => "addition of two things not detected"
      }
    
    '{ ($expr, ${Expr(message)}) }
  }


  def normalTest(expr: Boolean): String = {
    if (expr) "Yay, we passed!... something"
    else "We did not pass"
  }

  inline def macroTest(inline somethingMakingStringBoolRaw: Boolean): String = 
    ${ macroTestImpl('somethingMakingStringBoolRaw) }

  def macroTestImpl(somethingMakingStringBoolRaw: Expr[Boolean])(given qctx: QuoteContext): Expr[String] = {
    import qctx.tasty.{given, _}
    val somethingMakingBool = somethingMakingStringBoolRaw.unseal.underlyingArgument.seal

    val theExpressionAst = somethingMakingBool.unseal.showExtractors
    val theExpressionCode = somethingMakingBool.unseal.show
    '{
      if ($somethingMakingStringBoolRaw) 
        "Yay, we passed!... " + ${Expr(theExpressionCode)} + "\nOtherwise Known as: " + ${Expr(theExpressionAst)}
      else 
        "We did not pass: " + ${Expr(theExpressionCode)} + "\nOtherwise Known as: " + ${Expr(theExpressionAst)}
    }
  }
}

