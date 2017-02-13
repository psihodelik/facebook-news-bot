package com.gu.facebook_news_bot.utils

import org.clulab.processors.corenlp.CoreNLPProcessor
import org.clulab.struct.Tree

import scala.collection.immutable.Queue

object Parser {
  private val nlpProcessor = new CoreNLPProcessor()

  def warmUp = nlpProcessor.annotate("warm") //Avoids delaying the response to the first request

  def getNouns(text: String): List[String] = {
    val doc = nlpProcessor.annotate(text)

    doc.sentences.toList.flatMap { sentence =>
      sentence.syntacticTree.map { tree =>
        getTerms(tree)
      }
    }.flatten
  }

  /**
    * @param consecutiveNouns  nouns which should be considered a single term, e.g. "Donald Duck"
    * @param overall           the final set of terms in this tree
    */
  private case class Terms(consecutiveNouns: Queue[String], overall: Queue[String])

  private def getTerms(tree: Tree): Queue[String] = {
    tree.children.map { children: Array[Tree] =>
      val result: Terms = children.foldLeft(Terms(Queue(),Queue())) { (terms, child) =>
        if (child.value.matches("^NN[A-Z]*")) {
          //The child of this node is a noun, add it to queue of consecutive nouns
          child.children.flatMap(children => children.headOption.map(_.value)).map { noun: String =>
            terms.copy(consecutiveNouns = terms.consecutiveNouns :+ noun)
          } getOrElse terms

        } else {
          /**
            * Not a noun:
            * 1. Combine any consecutiveNouns into a single term
            * 2. Recursively get any terms from this child tree
            */
          val currentTerm = if (terms.consecutiveNouns.isEmpty) None else Some(terms.consecutiveNouns.mkString(" "))
          val childTerms = getTerms(child)
          val toAppend = currentTerm.map(noun => childTerms :+ noun).getOrElse(childTerms)

          terms.copy(overall = terms.overall ++ toAppend, consecutiveNouns = Queue())
        }
      }

      val remainder = if (result.consecutiveNouns.isEmpty) None else Some(result.consecutiveNouns.mkString(" "))
      remainder.map(result.overall :+ _).getOrElse(result.overall)
    } getOrElse Queue()
  }
}
