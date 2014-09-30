package org.allenai.parse

import edu.stanford.nlp.parser.lexparser.LexicalizedParser
import edu.stanford.nlp.process.CoreLabelTokenFactory
import edu.stanford.nlp.process.PTBTokenizer
import java.io.StringReader
import scala.collection.JavaConversions._
import edu.stanford.nlp.trees.ModCollinsHeadFinder

object QuestionParser {
  val parser = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz")
  parser.setOptionFlags("-outputFormat", "penn,typedDependencies", "-retainTmpSubcategories")
  val tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "")

  def parseQuestion(question: String) = {
    val tokenized = tokenizerFactory.getTokenizer(new StringReader(question)).tokenize()
    val parse = parser.parseTree(tokenized)
    parse.percolateHeads(new ModCollinsHeadFinder)
    parse
  }

  def test(args: Array[String]) {
    val parse = parseQuestion("On what island is Mt. Pinatubo?")
    parse.pennPrint()
    println(parse.dependencies)
    println(parse.dependencies.toList(0).name)
  }
}
