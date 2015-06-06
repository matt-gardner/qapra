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
    edges.toSet should contain theSameElementsAs Set(
      ("Q1:is", "nsubj", "Q1:Sandra"),
      ("Q1:is", "prep_in", "Q1:bathroom"),
      ("Q1:bathroom", "det", "Q1:the"),
      ("1:travelled", "nsubj", "1:Sandra"),
      ("1:travelled", "prep_to", "1:office"),
      ("1:office", "det", "1:the"),
      ("2:went", "nsubj", "2:Sandra"),
      ("2:went", "prep_to", "2:bathroom"),
      ("2:bathroom", "det", "2:the"),
      ("3:went", "nsubj", "3:Mary"),
      ("3:went", "prep_to", "3:bedroom"),
      ("3:bedroom", "det", "3:the"),
      ("4:moved", "nsubj", "4:Daniel"),
      ("4:moved", "prep_to", "4:hallway"),
      ("4:hallway", "det", "4:the"),
      ("Q1:Sandra", "instance", "1:Sandra"),
      ("Q1:Sandra", "instance", "2:Sandra"),
      ("Q1:Sandra", "last instance", "2:Sandra"),
      ("2:Sandra", "last instance", "1:Sandra"),
      ("Q1:bathroom", "instance", "2:bathroom"),
      ("Q1:bathroom", "last instance", "2:bathroom"),
      ("Q1:the", "instance", "1:the"),
      ("Q1:the", "instance", "2:the"),
      ("Q1:the", "instance", "3:the"),
      ("Q1:the", "instance", "4:the"),
      ("Q1:the", "last instance", "4:the"),
      ("4:the", "last instance", "3:the"),
      ("3:the", "last instance", "2:the"),
      ("2:the", "last instance", "1:the")
    )
  }
}
