package org.allenai.qapra.babi

import org.scalatest._

import org.allenai.parse.StanfordParser
import org.allenai.parse.ParsedSentence

import edu.cmu.ml.rtw.users.matt.util.FakeFileUtil

class DataProcessorSpec extends FlatSpecLike with Matchers {

  val question1_filename = "question 1 file"
  val question1_file_contents =
    """1 Mary moved to the bathroom.
    |2 John went to the hallway.
    |3 Where is Mary? 	bathroom	1
    |4 Daniel went back to the hallway.
    |5 Sandra moved to the garden.
    |6 Where is Daniel? 	hallway	4
    |1 Sandra travelled to the office.
    |2 Sandra went to the bathroom.
    |3 Where is Sandra? 	bathroom	2
    |4 Mary went to the bedroom.
    |5 Daniel moved to the hallway.
    |6 Where is Sandra? 	bathroom	2
    |7 John went to the garden.
    |8 John travelled to the office.
    |9 Where is Sandra? 	bathroom	2""".stripMargin

  val fileUtil = new FakeFileUtil
  fileUtil.addFileToBeRead(question1_filename, question1_file_contents)

  val processor = new DataProcessor(fileUtil)
  val parser = new StanfordParser

  val question1_example = ("Where is Sandra?", "bathroom", Seq("Sandra travelled to the office.",
    "Sandra went to the bathroom.", "Mary went to the bedroom.", "Daniel moved to the hallway."))
  val question1_history = question1_example._3.map(s =>
      parser.parseSentence(s).asInstanceOf[ParsedSentence])

  "extractQuestions" should "split a question file correctly" in {
    val questions = processor.extractQuestions(question1_filename)
    questions.size should be(5)
    questions(0)._1 should be("Where is Mary?")
    questions(0)._2 should be("bathroom")
    questions(0)._3 should be(Seq("Mary moved to the bathroom.", "John went to the hallway."))
    questions(1)._1 should be("Where is Daniel?")
    questions(1)._2 should be("hallway")
    questions(1)._3 should be(Seq("Mary moved to the bathroom.", "John went to the hallway.",
      "Daniel went back to the hallway.", "Sandra moved to the garden."))
    questions(2)._1 should be("Where is Sandra?")
    questions(2)._2 should be("bathroom")
    questions(2)._3 should be(Seq("Sandra travelled to the office.", "Sandra went to the bathroom."))
    questions(3)._1 should be("Where is Sandra?")
    questions(3)._2 should be("bathroom")
    questions(3)._3 should be(Seq("Sandra travelled to the office.", "Sandra went to the bathroom.",
      "Mary went to the bedroom.", "Daniel moved to the hallway."))
    questions(4)._1 should be("Where is Sandra?")
    questions(4)._2 should be("bathroom")
    questions(4)._3 should be(Seq("Sandra travelled to the office.", "Sandra went to the bathroom.",
      "Mary went to the bedroom.", "Daniel moved to the hallway.", "John went to the garden.",
      "John travelled to the office."))
  }

  "getSourceNodeFromQuestion" should "return the right source node" in {
    processor.getSourceNodeFromQuestion("Where is Sandra?", 1) should be("Q1:Sandra")
  }

  "getCandidatesFromHistory" should "find all nouns in the question history" in {
    val candidates = processor.getCandidatesFromHistory(question1_history)
    candidates.size should be(7)
    candidates should be(Set("Sandra", "Mary", "Daniel", "office", "bathroom", "bedroom", "hallway"))
  }

  "convertQuestionAnswerToSentence" should "convert 'where is' questions correctly" in {
    processor.convertQuestionAnswerToSentence("Where is Sandra?")("bathroom") should be(
      "Sandra is in the bathroom.")
  }

  "getGraphFromQuestion" should "construct a decent graph" in {
    val edges = processor.getGraphFromQuestion(question1_history, 1)("Sandra is in the bathroom")
    val expected = Set(
      ("Q1:KEEP:is", "nsubj", "Q1:REMOVE:Sandra"),
      ("Q1:KEEP:is", "prep_in", "Q1:REMOVE:bathroom"),
      ("Q1:REMOVE:bathroom", "det", "Q1:REMOVE:the"),
      ("1:KEEP:travelled", "nsubj", "1:REMOVE:Sandra"),
      ("1:KEEP:travelled", "prep_to", "1:REMOVE:office"),
      ("1:REMOVE:office", "det", "1:REMOVE:the"),
      ("2:KEEP:went", "nsubj", "2:REMOVE:Sandra"),
      ("2:KEEP:went", "prep_to", "2:REMOVE:bathroom"),
      ("2:REMOVE:bathroom", "det", "2:REMOVE:the"),
      ("3:KEEP:went", "nsubj", "3:REMOVE:Mary"),
      ("3:KEEP:went", "prep_to", "3:REMOVE:bedroom"),
      ("3:REMOVE:bedroom", "det", "3:REMOVE:the"),
      ("4:KEEP:moved", "nsubj", "4:REMOVE:Daniel"),
      ("4:KEEP:moved", "prep_to", "4:REMOVE:hallway"),
      ("4:REMOVE:hallway", "det", "4:REMOVE:the"),
      ("Q1:REMOVE:Sandra", "instance", "1:REMOVE:Sandra"),
      ("Q1:REMOVE:Sandra", "instance", "2:REMOVE:Sandra"),
      ("Q1:REMOVE:Sandra", "last instance", "2:REMOVE:Sandra"),
      ("2:REMOVE:Sandra", "last instance", "1:REMOVE:Sandra"),
      ("Q1:REMOVE:bathroom", "instance", "2:REMOVE:bathroom"),
      ("Q1:REMOVE:bathroom", "last instance", "2:REMOVE:bathroom"),
      ("Q1:REMOVE:the", "instance", "1:REMOVE:the"),
      ("Q1:REMOVE:the", "instance", "2:REMOVE:the"),
      ("Q1:REMOVE:the", "instance", "3:REMOVE:the"),
      ("Q1:REMOVE:the", "instance", "4:REMOVE:the"),
      ("Q1:REMOVE:the", "last instance", "4:REMOVE:the"),
      ("4:REMOVE:the", "last instance", "3:REMOVE:the"),
      ("3:REMOVE:the", "last instance", "2:REMOVE:the"),
      ("2:REMOVE:the", "last instance", "1:REMOVE:the")
    )
    val missing = expected -- edges.toSet
    if (missing.size != 0) {
      println("Missing edges:")
      println(missing)
    }
    val extra = edges.toSet -- expected
    if (extra.size != 0) {
      println("Extra edges:")
      println(extra)
    }
    missing.size should be(0)
    extra.size should be(0)
    edges.toSet should contain theSameElementsAs expected
  }
}
