package io.getquill.context

import scala.language.higherKinds
import scala.language.experimental.macros
//import io.getquill.dsl.Dsl
//import io.getquill.util.Messages.fail
import java.io.Closeable
import scala.compiletime.summonFrom
import scala.util.Try
import io.getquill.{ ReturnAction }
import miniquill.quoter.Query
import miniquill.dsl.EncodingDsl
import miniquill.quoter.Quoted
import miniquill.quoter.QueryMeta
import io.getquill.derived._
import miniquill.context.mirror.MirrorDecoders
import miniquill.context.mirror.Row
import miniquill.dsl.GenericDecoder
import miniquill.quoter.ScalarPlanter
import io.getquill.ast.Ast
import io.getquill.ast.ScalarTag
import scala.quoted.{Type => TType, _}
import io.getquill.idiom.Idiom
import io.getquill.ast.{Transform, QuotationTag}
import miniquill.quoter.QuotationBin
import miniquill.quoter.QuotedExpr
import miniquill.quoter.ScalarPlanterExpr
import io.getquill.idiom.ReifyStatement

import io.getquill._

object QueryMetaExtractor {
  import miniquill.parser._
  import scala.quoted._ // summonExpr is actually from here
  import scala.quoted.matching._ // ... or from here
  import miniquill.quoter.ScalarPlanter
  import miniquill.quoter._
  import io.getquill.ast.FunctionApply

  inline def run[T, R, D <: io.getquill.idiom.Idiom, N <: io.getquill.NamingStrategy](
    inline quotedRaw: Quoted[Query[T]],
    inline ctx: Context[D, N]
  ): (Quoted[Query[R]], R => T, Option[(String, List[ScalarPlanter[_, _]])]) = 
    ${ runImpl[T, R, D, N]('quotedRaw, 'ctx) }

  def runImpl[T: Type, R: Type, D <: io.getquill.idiom.Idiom: Type, N <: io.getquill.NamingStrategy: Type](
    quotedRaw: Expr[Quoted[Query[T]]],
    ctx: Expr[Context[D, N]]
  )(given qctx:QuoteContext): Expr[(Quoted[Query[R]], R => T, Option[(String, List[ScalarPlanter[_,_]])])] = {
    import qctx.tasty.{Try => TTry, _, given _}
    val quotedArg = quotedRaw.unseal.underlyingArgument.seal.cast[Quoted[Query[T]]]

    summonExpr(given '[QueryMeta[T, R]]) match {
      case Some(queryMeta) =>  
        queryMeta match {
          case '{ ($qmm: QueryMeta[T, R]) } =>

            val inlineQuoteOpt = QuotedExpr.inlineWithListOpt(quotedArg)
            (inlineQuoteOpt, qmm) match {
              // TODO Need a case where these are not matched
              case (Some((quotedExr, quotedExprLifts)), QuotationBinExpr.InlineOrPlucked(quotationBin)) =>
                println("~~~~~~~~~~~~~~~~~~~~~~~ Matched Quote Meta ~~~~~~~~~~~~~~~~~~~~~~~")

                quotationBin match {
                  // todo try astMappingFunc rename to `Ast(T => r)` or $r
                  case InlineableQuotationBinExpr(uid, astMappingFunc, _, quotation, lifts, List(extractor)) => 
                    // Don't need to unlift the ASTs and re-lift them. Just put them into a FunctionApply
                    val astApply = 
                      '{FunctionApply($astMappingFunc, List(${quotedExr.ast}))}

                    // TODO Dedupe?
                    val newLifts = (lifts ++ quotedExprLifts).map(_.plant)

                    

                    // In the compile-time case, we can synthesize the new quotation
                    // much more easily since we can just combine the lifts and Apply the
                    // QueryMeta quotation to the initial one
                    
                    // Syntheize a new quotation to combine the lifts and quotes of the reapplied
                    // query. I do not want to use QuoteMacro here because that requires a parser
                    // which means that the Context will require a parser as well. That will
                    // make the parser harder to customize by users
                    val reappliedQuery =
                      '{ Quoted[Query[R]]($astApply, ${Expr.ofList(newLifts)}, List()) }

                    val extractorFunc = '{ $extractor.asInstanceOf[R => T] }

                    val staticTranslation = StaticTranslationMacro[R, D, N](reappliedQuery)

                    '{ ($reappliedQuery, $extractorFunc, $staticTranslation) }

                    //StaticTranslationMacro[R, D, N](reappliedQuery, context)
                    
                  case PluckableQuotationBinExpr(uid, astTree, _) => 
                    qctx.throwError("Runtime-only query schemas are not allowed for now")
                }
            }

            //printer.lnf(r.unseal)
          case _ =>
            println("~~~~~~~~~~~~~~~~~~~~~~~ NOT Matched Quote Meta ~~~~~~~~~~~~~~~~~~~~~~~")
            // TODO Should probably check them one at a time to see if they are correct
            qctx.throwError("Invalid Quotation or Quote Meta")
        }
        
        //qctx.throwError("Quote Meta Identified but not found!")
      case None => 
        println("=============== Query Meta NOT FOUND! ============")
        qctx.throwError("Quote Meta needed but not found!")
    }
  }
}
