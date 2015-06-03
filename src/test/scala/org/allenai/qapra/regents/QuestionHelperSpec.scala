package org.allenai.qapra.regents

import org.scalatest._

import java.io.StringReader

import scala.collection.mutable
import scalax.io.Resource

class QuestionHelperSpec extends FlatSpecLike with Matchers {

  val question_text_1 = "Question text 1"
  val question_1_a_triple_1_str = "*(A1-arg1, A1-rel, A1-arg2)"
  val question_1_a_triple_1 = (("a1-arg1", "a1-rel", "a1-arg2"), false)
  val question_1_a_triple_2_str = "*(A2-arg1, A2-rel, A2-arg2)"
  val question_1_a_triple_2 = (("a2-arg1", "a2-rel", "a2-arg2"), false)
  val question_1_b_triple_1_str = "(B-arg1, B-rel, B-arg2)"
  val question_1_b_triple_1 = (("b-arg1", "b-rel", "b-arg2"), true)
  val question_2_a_triple_1_str = "(arg1, rel, arg2)"
  val question_2_a_triple_1 = (("arg1", "rel", "arg2"), true)
  val question_text_2 = "Question text 2"

  val questionLines = Seq(
    question_text_1,
    "A:",
    question_1_a_triple_1_str,
    question_1_a_triple_2_str,
    "B:+",
    question_1_b_triple_1_str,
    "",
    question_text_2,
    "A:+",
    question_2_a_triple_1_str,
    ""
  )

  val questionSet = List(
    Question(
      question_text_1,
      List(
        Choice(
          "A",
          List(question_1_a_triple_1, question_1_a_triple_2),
          false),
        Choice(
          "B",
          List(question_1_b_triple_1),
          true))),
    Question(
      question_text_2,
      List(
        Choice(
          "A",
          List(question_2_a_triple_1),
          true)))
  )

  "ReadQuestionsFromLines" should "correctly read a simple question set" in {
    val resource = Resource.fromReader(new StringReader(questionLines.mkString("\n") + "\n"))
    QuestionHelper.readQuestionsFromLines(resource.lines()) should be(questionSet)
  }

}
